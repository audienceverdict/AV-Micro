package com.slokam.social.assignments;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VideoAssignmentService {
  private final JdbcTemplate jdbc;
  private final AudienceDirectory users;
  public VideoAssignmentService(JdbcTemplate jdbc, AudienceDirectory users) { this.jdbc = jdbc; this.users = users; }
  public static boolean manager(Authentication actor) {
    return actor.getAuthorities().stream().anyMatch(a -> Set.of("ROLE_ADMIN", "ROLE_SOCIAL_MEDIA_MANAGER").contains(a.getAuthority()));
  }
  public static void requireManager(Authentication actor) {
    if (!manager(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or social media manager access required");
  }
  public record Result(int created, int alreadyAssigned) {}
  @io.swagger.v3.oas.annotations.media.Schema(name="AudienceAssignmentUpload")
  public record Upload(long id, String platform, String title, String permalink, String thumbnailUrl, Long viewCount, Long likeCount, Long commentCount,
      java.time.LocalDateTime metricsUpdatedAt) {}
  @io.swagger.v3.oas.annotations.media.Schema(name="AudienceVideoAssignment")
  public record Assignment(String id, String videoId, String userId, String assignedBy, String source,
      java.time.Instant assignedAt, String title, String description, List<Upload> uploads) {}
  @io.swagger.v3.oas.annotations.media.Schema(name="AudienceVideoAssignmentPage")
  public record Page(List<Assignment> items, int page, int size, long totalElements, long totalPages, boolean hasNext) {}

  @Transactional
  public Result assign(List<String> videos, List<String> recipients, Authentication actor, boolean self) {
    if (!self) requireManager(actor);
    var videoIds = new TreeSet<>(videos);
    var userIds = self ? Set.of(actor.getName()) : new TreeSet<>(recipients);
    for (String id : videoIds) {
      if (jdbc.queryForObject("SELECT COUNT(*) FROM application_videos WHERE id=?", Long.class, id) == 0)
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application video not found: " + id);
    }
    for (String id : userIds) users.requireEnabled(id);
    boolean admin = actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    String source = self ? "SELF" : admin ? "ADMIN" : "SOCIAL_MEDIA_MANAGER";
    int created = 0;
    for (String video : videoIds) for (String user : userIds) {
      // A duplicate preserves its original assigner and timestamp; other SQL failures must roll back.
      try {
        created += jdbc.update("INSERT INTO video_user_assignments(id,video_id,user_id,assigned_by,assignment_source) VALUES(?,?,?,?,?)",
            UUID.randomUUID().toString(), video, user, actor.getName(), source);
      } catch (org.springframework.dao.DuplicateKeyException duplicate) {
        // Unique(video_id,user_id) makes retries and concurrent assignments idempotent.
      }
    }
    return new Result(created, videoIds.size() * userIds.size() - created);
  }

  public record UnassignResult(int removed, int alreadyUnassigned) {}

  @Transactional
  public UnassignResult unassign(List<String> videos, List<String> recipients, Authentication actor, boolean self) {
    if (!self && actor.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required to unassign other users");
    var videoIds = new TreeSet<>(videos);
    var userIds = self ? Set.of(actor.getName()) : new TreeSet<>(recipients);
    int removed = 0;
    for (String video : videoIds) for (String user : userIds)
      removed += jdbc.update("DELETE FROM video_user_assignments WHERE video_id=? AND user_id=?", video, user);
    return new UnassignResult(removed, videoIds.size() * userIds.size() - removed);
  }

  @Transactional(readOnly = true)
  public Page list(Authentication actor, String userId, String videoId, String assignedBy, String source, int page, int size) {
    if (page < 0 || page > 1000000 || size < 1 || size > 100)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
    if (!manager(actor) && !actor.getName().equals(userId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may read only your own assignments");
    String where = " FROM video_user_assignments a JOIN application_videos v ON v.id=a.video_id WHERE 1=1";
    var args = new ArrayList<Object>();
    for (var filter : List.of(new String[]{"a.user_id", userId}, new String[]{"a.video_id", videoId},
        new String[]{"a.assigned_by", assignedBy}, new String[]{"a.assignment_source", source})) {
      if (filter[1] != null) { where += " AND " + filter[0] + "=?"; args.add(filter[1]); }
    }
    long count = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
    args.add(size); args.add((long) page * size);
    var items = jdbc.queryForList("SELECT a.id,a.video_id AS videoId,a.user_id AS userId,a.assigned_by AS assignedBy,"
        + "a.assignment_source AS source,a.assigned_at AS assignedAt,v.title,v.description" + where
        + " ORDER BY a.assigned_at DESC,a.id DESC LIMIT ? OFFSET ?", args.toArray());
    for (var item : items) {
      var linkedUploads = jdbc.queryForList(
        "SELECT c.id,c.platform,c.title,c.permalink,c.thumbnail_url AS thumbnailUrl,c.engagement_metadata,c.retrieved_at FROM social_content c"
        + " JOIN application_video_uploads u ON u.content_id=c.id WHERE u.video_id=? ORDER BY c.id", item.get("videoId"));
      com.slokam.social.api.PortalMetrics.enrich(jdbc, linkedUploads);
      item.put("uploads", linkedUploads);
    }
    var assignments = items.stream().map(item -> new Assignment(
        (String)item.get("id"), (String)item.get("videoId"), (String)item.get("userId"),
        (String)item.get("assignedBy"), (String)item.get("source"),
        ((java.sql.Timestamp)item.get("assignedAt")).toInstant(), (String)item.get("title"),
        (String)item.get("description"), uploads(item.get("uploads")))).toList();
    return new Page(assignments, page, size, count, (count + size - 1) / size, ((long) page + 1) * size < count);
  }
  @SuppressWarnings("unchecked")
  private List<Upload> uploads(Object rows) {
    return ((List<Map<String,Object>>) rows).stream().map(row -> new Upload(
        ((Number)row.get("id")).longValue(), (String)row.get("platform"), (String)row.get("title"),
        (String)row.get("permalink"), (String)row.get("thumbnailUrl"),
        (Long)row.get("viewCount"), (Long)row.get("likeCount"), (Long)row.get("commentCount"),
        (java.time.LocalDateTime)row.get("metricsUpdatedAt"))).toList();
  }
}
