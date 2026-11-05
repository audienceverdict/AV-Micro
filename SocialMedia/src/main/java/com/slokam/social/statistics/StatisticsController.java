package com.slokam.social.statistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Statistics")
@RestController
@RequestMapping("/api")
@Validated
public class StatisticsController {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  public StatisticsController(JdbcTemplate j, ObjectMapper m) { jdbc=j; mapper=m; }
  private static final String CURRENT = "SELECT t.id AS task_id,t.account_id,t.content_id,a.platform,t.operation,t.enabled,t.status,t.last_attempt_at,t.last_success_at,t.due_at,t.error_message,t.result FROM social_statistics_tasks t JOIN social_accounts a ON a.id=t.account_id ";
  private static final String HISTORY = "SELECT h.id,h.task_id,t.account_id,t.content_id,a.platform,t.operation,h.snapshot_bucket,h.observed_at,h.window_from,h.window_to,h.result FROM social_statistics_history h JOIN social_statistics_tasks t ON t.id=h.task_id JOIN social_accounts a ON a.id=t.account_id ";

  @Operation(summary = "List stored accounts", description = "Discovers local account IDs and their configured enablement.")
  @GetMapping("/statistics/accounts")
  public List<Map<String,Object>> accounts() {
    return jdbc.queryForList("SELECT id,platform,external_account_id,account_name,enabled FROM social_accounts ORDER BY id");
  }
  @Operation(summary = "Get current account metrics", description = "Account-scope tasks only. Continue with the last task_id as afterId. A stored successful result can remain visible after a later failure; check status and last_success_at.")
  @GetMapping("/accounts/{accountId}/metrics")
  public List<Map<String,Object>> account(@Parameter(description = "Local social_accounts.id, not the provider account ID.") @PathVariable @Positive long accountId,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue="0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue="100") @Min(1) @Max(200) int limit) {
    exists("social_accounts", accountId);
    return read(CURRENT + "WHERE t.account_id=? AND t.content_id=0 AND t.id>? ORDER BY t.id LIMIT ?", accountId, afterId, limit);
  }
  @Operation(summary = "Get current upload metrics", description = "Current statistics tasks for a local content ID. Continue with the last task_id. Results preserve provider JSON and metric definitions; missing values are not zero.")
  @GetMapping("/uploads/{contentId}/metrics")
  public List<Map<String,Object>> upload(@Parameter(description = "Local social_content.id, not the provider content ID.") @PathVariable @Positive long contentId,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue="0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue="100") @Min(1) @Max(200) int limit) {
    exists("social_content", contentId);
    return read(CURRENT + "WHERE t.content_id=? AND t.id>? ORDER BY t.id LIMIT ?", contentId, afterId, limit);
  }
  @Operation(summary = "Get current video metrics", description = "Returns tasks for currently linked uploads without summing across providers. Continue with the last task_id.")
  @GetMapping("/videos/{videoId}/metrics")
  public List<Map<String,Object>> video(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue="0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue="100") @Min(1) @Max(200) int limit) {
    exists("application_videos", videoId);
    return read(CURRENT + "JOIN application_video_uploads u ON u.content_id=t.content_id WHERE u.video_id=? AND t.id>? ORDER BY t.id LIMIT ?", videoId, afterId, limit);
  }
  @Operation(summary = "Get account metric history", description = "Successful bucketed snapshots. Continue with the last history id. Provider reporting periods remain unchanged.")
  @GetMapping("/accounts/{accountId}/metrics/history")
  public List<Map<String,Object>> accountHistory(@Parameter(description = "Local social_accounts.id, not the provider account ID.") @PathVariable @Positive long accountId,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue="0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue="100") @Min(1) @Max(200) int limit) {
    exists("social_accounts", accountId);
    return read(HISTORY + "WHERE t.account_id=? AND t.content_id=0 AND h.id>? ORDER BY h.id LIMIT ?", accountId, afterId, limit);
  }
  @Operation(summary = "Get upload metric history", description = "Successful snapshots for a local content ID. Continue with the last history id.")
  @GetMapping("/uploads/{contentId}/metrics/history")
  public List<Map<String,Object>> uploadHistory(@Parameter(description = "Local social_content.id, not the provider content ID.") @PathVariable @Positive long contentId,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue="0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue="100") @Min(1) @Max(200) int limit) {
    exists("social_content", contentId);
    return read(HISTORY + "WHERE t.content_id=? AND h.id>? ORDER BY h.id LIMIT ?", contentId, afterId, limit);
  }
  @Operation(summary = "Get video metric history", description = "History follows current video assignments, including after reassignment. Continue with the last history id.")
  @GetMapping("/videos/{videoId}/metrics/history")
  public List<Map<String,Object>> videoHistory(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue="0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue="100") @Min(1) @Max(200) int limit) {
    exists("application_videos", videoId);
    return read(HISTORY + "JOIN application_video_uploads u ON u.content_id=t.content_id WHERE u.video_id=? AND h.id>? ORDER BY h.id LIMIT ?", videoId, afterId, limit);
  }
  private void exists(String table, Object id) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?", Long.class, id)==0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Statistics target not found");
  }
  private List<Map<String,Object>> read(String sql, Object... args) {
    var rows = jdbc.queryForList(sql,args);
    for (var row : rows) {
      Object result=row.get("result");
      if (result!=null) try { row.put("result", mapper.readTree(result.toString())); }
      catch (Exception e) { throw new IllegalStateException("Invalid stored statistics result"); }
    }
    return rows;
  }
}
