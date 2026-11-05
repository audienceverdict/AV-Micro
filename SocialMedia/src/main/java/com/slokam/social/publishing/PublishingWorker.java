package com.slokam.social.publishing;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@lombok.extern.slf4j.Slf4j
public class PublishingWorker {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final PublishingProperties properties;
  private final SocialPublisher publisher;
  private final PublishingTokens tokens;
  public PublishingWorker(JdbcTemplate jdbc, TransactionTemplate transactions, PublishingProperties properties,
      SocialPublisher publisher, PublishingTokens tokens) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.properties = properties;
    this.publisher = publisher;
    this.tokens = tokens;
  }

  @Scheduled(scheduler = "publishingTaskScheduler", fixedDelayString = "${social.publishing.interval-ms:15000}",
      initialDelayString = "${social.publishing.initial-delay-ms:15000}")
  public void run() {
    if (!properties.isEnabled()) return;
    // A crashed worker may have sent a successful POST. Never re-send automatically.
    jdbc.update("UPDATE social_publications SET status='UNKNOWN',error_message='Worker stopped before recording the remote result; reconcile with provider before retrying',updated_at=UTC_TIMESTAMP(6) WHERE status='RUNNING' AND started_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR)");
    jdbc.update("UPDATE social_publications SET status='ACTION_REQUIRED',error_message='Publishing or processing exceeded 24 hours; inspect provider before continuing',updated_at=UTC_TIMESTAMP(6) WHERE status IN ('QUEUED','PROCESSING','CHECKING') AND deadline_at < UTC_TIMESTAMP(6)");
    for (int i = 0; i < properties.getBatchSize(); i++) {
      Publication job = claim();
      if (job == null) return;
      try {
        var result = publisher.advance(job);
        jdbc.update("UPDATE social_publications SET status=?,resume_status=?,remote_reference=COALESCE(?,remote_reference),external_content_id=COALESCE(?,external_content_id),permalink=COALESCE(?,permalink),error_message=NULL,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 SECOND),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='RUNNING'",
            result.status(), result.status(), result.reference(), result.externalId(), result.permalink(), job.id());
        log.info("Publishing job={} platform={} status={}", job.id(), job.platform(), result.status());
      } catch (PublishFailure failure) {
        String state = failure.status();
        if (state.equals("ACTION_REQUIRED")) tokens.invalidate(job.platform(), job.accountId());
        // A saved YouTube session can be queried before resending any bytes.
        if (state.equals("RETRY") || (state.equals("UNKNOWN") && job.platform().equals("youtube") && job.stage().equals("PROCESSING")))
          state = job.stage();
        jdbc.update("UPDATE social_publications SET status=?,error_message=?,next_attempt_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='RUNNING'",
            state, failure.getMessage(), failure.retrySeconds(), job.id());
        log.warn("Publishing job={} platform={} status={} reason={}", job.id(), job.platform(), state, failure.getMessage());
      } catch (Exception failure) {
        // Includes a failed DB write after a successful remote request. Preserve uncertainty.
        jdbc.update("UPDATE social_publications SET status='UNKNOWN',error_message='Unable to record publishing result; reconcile with provider',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='RUNNING'", job.id());
        log.warn("Publishing result uncertain job={} platform={}", job.id(), job.platform());
      }
    }
  }

  Publication claim() {
    return transactions.execute(tx -> {
      var rows = jdbc.queryForList("SELECT id FROM social_publications WHERE status IN ('QUEUED','PROCESSING','CHECKING') AND next_attempt_at <= UTC_TIMESTAMP(6) ORDER BY next_attempt_at,created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
      if (rows.isEmpty()) return null;
      String id = (String) rows.getFirst().get("id");
      var jobs = jdbc.query("SELECT p.*,c.title,c.caption,c.content_type,c.media_type,c.media_size,c.youtube_privacy FROM social_publications p JOIN application_content c ON c.id=p.content_id WHERE p.id=?",
          (rs, index) -> new Publication(rs.getString("id"), rs.getString("content_id"), rs.getString("platform"),
              rs.getString("external_account_id"), rs.getString("status"), rs.getString("remote_reference"),
              rs.getString("external_content_id"), rs.getString("title"), rs.getString("caption"),
              rs.getString("content_type"), rs.getString("media_type"), rs.getLong("media_size"), rs.getString("youtube_privacy")), id);
      var job = jobs.getFirst();
      jdbc.update("UPDATE social_publications SET resume_status=status,status='RUNNING',attempts=attempts+1,started_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?", id);
      return job;
    });
  }
}
