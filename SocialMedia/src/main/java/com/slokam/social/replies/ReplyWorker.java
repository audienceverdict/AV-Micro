package com.slokam.social.replies;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.publishing.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class ReplyWorker {
  private final JdbcTemplate jdbc; private final TransactionTemplate tx;
  private final SocialProperties properties; private final ReplyProperties settings;
  private final List<CommentReplyProvider> providers;
  public ReplyWorker(JdbcTemplate jdbc,TransactionTemplate tx,SocialProperties properties,ReplyProperties settings,List<CommentReplyProvider> providers) {
    this.jdbc=jdbc;this.tx=tx;this.properties=properties;this.settings=settings;this.providers=providers;
  }
  @Scheduled(scheduler="publishingTaskScheduler",fixedDelayString="${social.replies.interval-ms:15000}",initialDelayString="${social.replies.initial-delay-ms:15000}")
  public void run() {
    if(!settings.isEnabled()) return;
    jdbc.update("UPDATE social_comment_replies SET status='UNKNOWN',error_message='Delivery interrupted; inspect provider before retrying',updated_at=UTC_TIMESTAMP(6) WHERE status='SENDING' AND started_at<DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR)");
    for(int i=0;i<10;i++) {
      var job=tx.execute(s -> {
        var rows=jdbc.queryForList("SELECT r.*,c.id AS target_content_id,m.external_comment_id,m.parent_external_id,c.external_content_id,c.platform,a.external_account_id FROM social_comment_replies r LEFT JOIN social_comments m ON m.id=r.comment_id JOIN social_content c ON c.id=COALESCE(m.content_id,r.content_id) JOIN social_accounts a ON a.id=c.account_id WHERE r.status='QUEUED' ORDER BY r.created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
        if(rows.isEmpty()) return null;
        var row=rows.getFirst(); jdbc.update("UPDATE social_comment_replies SET status='SENDING',started_at=UTC_TIMESTAMP(6),attempts=attempts+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",row.get("id")); return row;
      });
      if(job==null) return;
      try {
        // Jobs enter this queue only through ADMIN-authenticated write endpoints.
        String platform=(String)job.get("platform");
        var config=properties.getPlatforms().get(platform);
        if(config==null || !config.isEnabled()) throw new PublishFailure("FAILED","Platform disabled");
        var account=config.getAccounts().stream().filter(a -> a.isEnabled() && a.getExternalId().equals(job.get("external_account_id"))).findFirst()
            .orElseThrow(() -> new PublishFailure("FAILED","Account is disabled or not configured"));
        var provider=providers.stream().filter(p->p.supports(platform)).findFirst().orElseThrow(()->new PublishFailure("FAILED","Unsupported platform"));
        String parent=job.get("comment_id")==null ? null : Objects.toString(job.get("parent_external_id"),Objects.toString(job.get("external_comment_id")));
        String remote=parent==null ? provider.post(platform,account,(String)job.get("external_content_id"),(String)job.get("reply_text")) : provider.send(platform,account,parent,(String)job.get("reply_text"));
        // Save the provider ID and local visible reply atomically. Never repeat a successful POST.
        tx.executeWithoutResult(s -> {
          if(jdbc.update("UPDATE social_comment_replies SET status='SENT',external_reply_id=?,error_message=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='SENDING'",remote,job.get("id"))!=1) return;
          jdbc.update("INSERT INTO social_comments(content_id,external_comment_id,parent_external_id,author_id,author_name,comment_text,published_at,retrieved_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE retrieved_at=UTC_TIMESTAMP(6)",job.get("target_content_id"),remote,parent,account.getExternalId(),account.getName(),job.get("reply_text"));
        });
      } catch(PublishFailure e) {
        String status=Set.of("UNKNOWN").contains(e.status())?"UNKNOWN":"FAILED";
        jdbc.update("UPDATE social_comment_replies SET status=?,error_message=?,next_attempt_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='SENDING'",status,e.getMessage(),e.status().equals("RETRY")?Math.max(1,e.retrySeconds()):0L,job.get("id"));
      } catch(Exception e) {
        jdbc.update("UPDATE social_comment_replies SET status='UNKNOWN',error_message='Delivery result could not be recorded; inspect provider',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='SENDING'",job.get("id"));
      }
    }
  }
}
