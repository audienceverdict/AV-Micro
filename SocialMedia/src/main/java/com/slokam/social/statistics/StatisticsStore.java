package com.slokam.social.statistics;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.persistence.AccountLeaseService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsStore {
  private final JdbcTemplate jdbc;
  private final AccountLeaseService leases;
  private final ObjectMapper mapper;
  public StatisticsStore(JdbcTemplate j, AccountLeaseService l, ObjectMapper m) {
    jdbc = j; leases = l; mapper = m;
  }
  public record Task(long id, StatisticsProvider.Target target, String operation, String cursor,
      LocalDate from, LocalDate to) {}

  @Transactional
  public void seed(SocialAccount a, String owner, StatisticsProvider provider, boolean enabled) {
    leases.guard(a.id(), owner);
    jdbc.update("UPDATE social_statistics_tasks SET enabled=FALSE WHERE account_id=?", a.id());
    if (!enabled) return;
    seedTarget(a, new StatisticsProvider.Target(0, a.externalAccountId(), "ACCOUNT", mapper.createObjectNode()), provider);
    // Iterate rows without retaining the complete account catalog in memory.
    jdbc.query("SELECT id,external_content_id,content_type,provider_metadata FROM social_content WHERE account_id=?",
        (org.springframework.jdbc.core.RowCallbackHandler) rs -> seedTarget(a,
            new StatisticsProvider.Target(rs.getLong("id"), rs.getString("external_content_id"),
                rs.getString("content_type"), json(rs.getString("provider_metadata"))), provider), a.id());
  }
  private void seedTarget(SocialAccount a, StatisticsProvider.Target t, StatisticsProvider provider) {
    for (String op : provider.operations(a, t))
      jdbc.update("INSERT INTO social_statistics_tasks(account_id,content_id,operation,due_at) VALUES(?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE enabled=TRUE",
          a.id(), t.contentId(), op);
  }

  public Optional<Task> next(SocialAccount a) {
    return jdbc.query("SELECT t.*,c.external_content_id,c.content_type,c.provider_metadata FROM social_statistics_tasks t LEFT JOIN social_content c ON c.id=t.content_id AND c.account_id=t.account_id WHERE t.account_id=? AND t.enabled=TRUE AND t.due_at<=UTC_TIMESTAMP(6) AND (t.content_id=0 OR c.id IS NOT NULL) ORDER BY t.due_at,t.id LIMIT 1",
        (rs,n) -> new Task(rs.getLong("id"), new StatisticsProvider.Target(rs.getLong("content_id"),
            rs.getLong("content_id") == 0 ? a.externalAccountId() : rs.getString("external_content_id"),
            rs.getString("content_type"), json(rs.getString("provider_metadata"))),
            rs.getString("operation"), rs.getString("page_cursor"),
            rs.getObject("window_from", LocalDate.class), rs.getObject("window_to", LocalDate.class)), a.id())
        .stream().findFirst();
  }

  @Transactional
  public Task start(long account, String owner, Task t, int lookbackDays) {
    leases.guard(account, owner);
    LocalDate to = t.to() == null ? LocalDate.now(ZoneOffset.UTC).minusDays(1) : t.to();
    LocalDate from = t.from() == null ? to.minusDays(lookbackDays - 1L) : t.from();
    jdbc.update("UPDATE social_statistics_tasks SET last_attempt_at=UTC_TIMESTAMP(6),window_from=?,window_to=? WHERE id=? AND account_id=?",
        from, to, t.id(), account);
    return new Task(t.id(), t.target(), t.operation(), t.cursor(), from, to);
  }

  @Transactional
  public void commit(long account, String owner, Task t, StatisticsProvider.Page page,
      long refresh, int snapshotSeconds, int maxPages) {
    leases.guard(account, owner);
    var row = jdbc.queryForMap("SELECT * FROM social_statistics_tasks WHERE id=? AND account_id=? FOR UPDATE", t.id(), account);
    // A replayed or superseded page must not append twice or overwrite newer data.
    if (row.get("window_from") == null || row.get("window_to") == null
        || !Objects.equals(t.cursor(), row.get("page_cursor"))
        || !Objects.equals(t.from(), ((java.sql.Date) row.get("window_from")).toLocalDate())
        || !Objects.equals(t.to(), ((java.sql.Date) row.get("window_to")).toLocalDate()))
      throw new IllegalStateException("Statistics page superseded");
    ArrayNode pages = array(row.get("progress"));
    ArrayNode seen = array(row.get("seen_cursors"));
    String next = page.nextCursor();
    if (next != null) {
      if (next.equals(t.cursor()) || java.util.stream.StreamSupport.stream(seen.spliterator(), false)
          .anyMatch(c -> c.asText().equals(next))) throw new IllegalArgumentException("Repeated statistics cursor");
      if (pages.size() + 1 >= maxPages) throw new IllegalArgumentException("Statistics pagination budget exceeded");
      seen.add(next);
    }
    if (page.data() == null || !page.data().isContainerNode()) throw new IllegalArgumentException("Invalid statistics page");
    pages.add(page.data());
    if (next != null) {
      jdbc.update("UPDATE social_statistics_tasks SET page_cursor=?,progress=?,seen_cursors=?,status='PENDING',error_message=NULL,due_at=TIMESTAMPADD(SECOND,1,UTC_TIMESTAMP(6)) WHERE id=?",
          next, pages.toString(), seen.toString(), t.id());
      return;
    }
    // Preserve provider names, units, dimensions, periods, reporting dates and structured values.
    String result = mapper.createObjectNode().put("operation", t.operation())
        .put("requestedFrom", t.from().toString()).put("requestedTo", t.to().toString())
        .set("pages", pages).toString();
    long bucket = Instant.now().getEpochSecond() / snapshotSeconds;
    jdbc.update("INSERT INTO social_statistics_history(task_id,snapshot_bucket,observed_at,window_from,window_to,result) VALUES(?,?,UTC_TIMESTAMP(6),?,?,?) ON DUPLICATE KEY UPDATE observed_at=VALUES(observed_at),window_from=VALUES(window_from),window_to=VALUES(window_to),result=VALUES(result)",
        t.id(), bucket, t.from(), t.to(), result);
    jdbc.update("UPDATE social_statistics_tasks SET result=?,last_success_at=UTC_TIMESTAMP(6),status='SUCCESS',error_message=NULL,page_cursor=NULL,progress=NULL,seen_cursors=NULL,window_from=NULL,window_to=NULL,due_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE id=?",
        result, refresh, t.id());
  }

  @Transactional
  public void fail(long account, String owner, Task t, String status, long retrySeconds) {
    leases.guard(account, owner);
    // Restart this operation's reporting window after failure; keep its last successful values.
    jdbc.update("UPDATE social_statistics_tasks SET status=?,error_message=?,page_cursor=NULL,progress=NULL,seen_cursors=NULL,window_from=NULL,window_to=NULL,due_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE id=? AND account_id=?",
        status, "Statistics unavailable; check credentials, permissions, metric/media support and provider quota", Math.max(60,retrySeconds), t.id(), account);
  }

  private ArrayNode array(Object value) {
    if (value == null) return mapper.createArrayNode();
    JsonNode node = json(value.toString());
    if (!node.isArray()) throw new IllegalArgumentException("Invalid statistics progress");
    return (ArrayNode) node;
  }
  private JsonNode json(String value) {
    if (value == null) return mapper.createObjectNode();
    try { return mapper.readTree(value); }
    catch (Exception e) { throw new IllegalArgumentException("Invalid stored statistics JSON"); }
  }
}
