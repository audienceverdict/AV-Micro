package com.slokam.social.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Safe, normalized lifetime counts shared by audience feed and assignment responses. */
public final class PortalMetrics {
  private static final ObjectMapper JSON = new ObjectMapper();
  private PortalMetrics() {}

  public static void enrich(JdbcTemplate jdbc, List<Map<String, Object>> items) {
    if (items.isEmpty()) return;
    var ids = items.stream().map(row -> row.get("id")).toList();
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
      var metrics = jdbc.queryForList("SELECT content_id,operation,status,last_success_at,result"
          + " FROM social_statistics_tasks WHERE content_id IN (" + placeholders + ")"
          + " AND result IS NOT NULL AND last_success_at IS NOT NULL"
          + " ORDER BY last_success_at,id", ids.toArray());
    for (var item : items) addMetrics(item, metrics);
  }

  private static void addMetrics(Map<String, Object> item, List<Map<String, Object>> tasks) {
    var observed = new HashMap<String, java.time.LocalDateTime>();
    Object stored = item.remove("engagement_metadata");
    var retrieved = time(item.remove("retrieved_at"));
    for (String field : List.of("viewCount", "likeCount", "commentCount")) item.put(field, null);
    if (stored != null) {
      JsonNode data = json(stored);
      for (String field : List.of("viewCount", "likeCount", "commentCount"))
        putCount(item, observed, field, data.get(field), retrieved);
      putCount(item, observed, "likeCount", data.get("like_count"), retrieved);
      putCount(item, observed, "commentCount", data.get("comments_count"), retrieved);
    }
    for (var task : tasks) {
      if (!Objects.equals(item.get("id"), task.get("content_id"))) continue;
      var at = time(task.get("last_success_at"));
      String operation = task.get("operation").toString();
      for (JsonNode page : json(task.get("result")).path("pages")) {
        if (operation.equals("public") && item.get("platform").equals("youtube")) {
          for (JsonNode video : page.path("items"))
            for (String field : List.of("viewCount", "likeCount", "commentCount"))
              putCount(item, observed, field, video.path("statistics").get(field), at);
        } else if (operation.equals("field:like_count")) {
          putCount(item, observed, "likeCount", page.get("like_count"), at);
        } else if (operation.equals("field:comments_count")) {
          putCount(item, observed, "commentCount", page.get("comments_count"), at);
        } else if (operation.equals("reaction:LIKE") && item.get("platform").equals("facebook")) {
          putCount(item, observed, "likeCount", page.path("reactions").path("summary").get("total_count"), at);
        } else if (operation.equals("field:comments.limit(0).summary(true)")) {
          putCount(item, observed, "commentCount", page.path("comments").path("summary").get("total_count"), at);
        } else if ((operation.equals("insight:views") && item.get("platform").equals("instagram"))
            || (operation.startsWith("video:") && operation.endsWith(":total_video_views")
                && item.get("platform").equals("facebook"))) {
          for (JsonNode metric : page.path("data"))
            if (Set.of("views", "total_video_views").contains(metric.path("name").asText())
                && metric.path("period").asText().equals("lifetime")) {
              JsonNode value = metric.path("total_value").get("value");
              if (value == null && metric.path("values").size() == 1)
                value = metric.path("values").get(0).get("value");
              putCount(item, observed, "viewCount", value, at);
            }
        }
      }
    }
    item.put("metricsUpdatedAt", observed.values().stream().filter(Objects::nonNull)
        .max(Comparator.naturalOrder()).orElse(null));
  }

  private static void putCount(Map<String, Object> item, Map<String, java.time.LocalDateTime> observed,
      String field, JsonNode value, java.time.LocalDateTime at) {
    if (value == null || value.isNull() || !value.asText().matches("[0-9]+")) return;
    var previous = observed.get(field);
    if (previous != null && (at == null || at.isBefore(previous))) return;
    try {
      item.put(field, Long.valueOf(value.asText()));
      observed.put(field, at);
    } catch (NumberFormatException ignored) { /* Unrepresentable counts remain unavailable. */ }
  }

  private static java.time.LocalDateTime time(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
    return (java.time.LocalDateTime) value;
  }

  private static JsonNode json(Object value) {
    try { return JSON.readTree(value.toString()); }
    catch (Exception e) { throw new IllegalStateException("Invalid stored portal metrics JSON"); }
  }
}
