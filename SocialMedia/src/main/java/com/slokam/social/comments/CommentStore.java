package com.slokam.social.comments;

import com.slokam.social.infrastructure.persistence.AccountLeaseService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentStore {
  private final JdbcTemplate jdbc;
  private final AccountLeaseService leases;

  public CommentStore(JdbcTemplate j, AccountLeaseService l) {
    jdbc = j;
    leases = l;
  }

  public record Task(long id, long contentId, String externalId, String parent, String cursor) {}

  @Transactional
  public void seed(long account, String owner) {
    leases.guard(account, owner);
    jdbc.update(
        "INSERT IGNORE INTO social_comment_tasks(content_id,due_at) SELECT id,UTC_TIMESTAMP(6) FROM social_content WHERE account_id=? AND ((platform='youtube' AND content_type IN ('VIDEO','SHORT_VIDEO','REEL','LIVE')) OR (platform IN ('instagram','facebook') AND content_type<>'STORY'))",
        account);
  }

  public Optional<Task> next(long account) {
    return jdbc
        .query(
            "SELECT t.*,c.external_content_id FROM social_comment_tasks t JOIN social_content c ON c.id=t.content_id WHERE c.account_id=? AND t.due_at<=UTC_TIMESTAMP(6) ORDER BY t.due_at,t.id LIMIT 1",
            (rs, n) ->
                new Task(
                    rs.getLong("id"),
                    rs.getLong("content_id"),
                    rs.getString("external_content_id"),
                    rs.getString("parent_external_id"),
                    rs.getString("page_cursor")),
            account)
        .stream()
        .findFirst();
  }

  @Transactional
  public void commit(long account, String owner, Task t, CommentPage page, long refreshSeconds) {
    leases.guard(account, owner);
    for (var c : page.comments()) {
      jdbc.update(
          "INSERT INTO social_comments(content_id,external_comment_id,parent_external_id,author_id,author_name,comment_text,published_at,provider_updated_at,retrieved_at,like_count,reply_count) VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),?,?) ON DUPLICATE KEY UPDATE parent_external_id=VALUES(parent_external_id),author_id=VALUES(author_id),author_name=VALUES(author_name),comment_text=VALUES(comment_text),published_at=VALUES(published_at),provider_updated_at=VALUES(provider_updated_at),retrieved_at=VALUES(retrieved_at),like_count=COALESCE(VALUES(like_count),like_count),reply_count=COALESCE(VALUES(reply_count),reply_count)",
          t.contentId(),
          c.id(),
          c.parentId(),
          c.authorId(),
          c.authorName(),
          c.text(),
          ts(c.publishedAt()),
          ts(c.updatedAt()),
          c.likes(),
          c.replies());
      if (t.parent().isEmpty() && (c.replies() == null || c.replies() > 0))
        jdbc.update(
            "INSERT IGNORE INTO social_comment_tasks(content_id,parent_external_id,due_at) VALUES(?,?,UTC_TIMESTAMP(6))",
            t.contentId(),
            c.id());
    }
    boolean more = page.nextCursor() != null;
    jdbc.update(
        "UPDATE social_comment_tasks SET page_cursor=?,status=?,error_message=NULL,pages_fetched=pages_fetched+1,due_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),last_success_at=IF(?,last_success_at,UTC_TIMESTAMP(6)) WHERE id=?",
        page.nextCursor(),
        more ? "PENDING" : "SUCCESS",
        more ? 1 : refreshSeconds,
        more,
        t.id());
  }

  @Transactional
  public void fail(long account, String owner, Task t, Duration delay) {
    fail(
        account,
        owner,
        t,
        delay,
        "Comment retrieval failed; check provider permissions, availability, quota or cursor");
  }

  @Transactional
  public void fail(long account, String owner, Task t, Duration delay, String reason) {
    leases.guard(account, owner);
    jdbc.update(
        "UPDATE social_comment_tasks SET status='FAILED',error_message=?,due_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE id=?",
        reason,
        Math.max(60, delay.toSeconds()),
        t.id());
  }

  @Transactional
  public void reset(long contentId) {
    var accounts =
        jdbc.queryForList(
            "SELECT account_id FROM social_content WHERE id=?", Long.class, contentId);
    if (accounts.isEmpty())
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Upload not found");
    long account = accounts.getFirst();
    String owner = leases.acquire(account);
    if (owner == null)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Account is synchronizing; retry later");
    try {
      jdbc.update(
          "UPDATE social_comment_tasks SET page_cursor=NULL,due_at=UTC_TIMESTAMP(6),status='PENDING',error_message=NULL WHERE content_id=?",
          contentId);
    } finally {
      leases.release(account, owner);
    }
  }

  public boolean coolingDown(long account) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM social_sync_checkpoints WHERE account_id=? AND retry_not_before>UTC_TIMESTAMP(6)",
            Long.class,
            account)
        > 0;
  }

  @Transactional
  public void defer(long account, String owner, Duration delay) {
    leases.guard(account, owner);
    jdbc.update(
        "UPDATE social_sync_checkpoints SET retry_not_before=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE account_id=?",
        delay.toSeconds(),
        account);
    jdbc.update(
        "UPDATE social_comment_tasks t JOIN social_content c ON c.id=t.content_id SET t.due_at=GREATEST(t.due_at,TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6))) WHERE c.account_id=?",
        delay.toSeconds(),
        account);
  }

  private Timestamp ts(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
