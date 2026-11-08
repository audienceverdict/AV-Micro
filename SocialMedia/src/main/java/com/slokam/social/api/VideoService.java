package com.slokam.social.api;

import com.slokam.social.application.VideoTitles;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VideoService {
  private final JdbcTemplate jdbc;

  public VideoService(JdbcTemplate j) {
    jdbc = j;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> create(String title, String description) {
    String key = VideoTitles.key(title);
    if (key == null || title.codePointCount(0, title.length()) > 255)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video title must contain 1-255 characters");
    String id = "VID-" + UUID.randomUUID();
    // The unique key serializes competing platform syncs and manual creates. A duplicate
    // must not overwrite the original display title, description or immutable ID.
    jdbc.update(
        "INSERT INTO application_videos(id,title,description,title_match_key,created_at,updated_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id",
        id,
        title,
        description,
        key);
    // Return the current locked row: a plain read can use an older REPEATABLE READ
    // snapshot that predates another transaction's winning insert.
    return jdbc.queryForMap(
        "SELECT * FROM application_videos WHERE title_match_key=? FOR UPDATE", key);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void autoAssign(long contentId) {
    var rows = jdbc.queryForList(
        "SELECT content_type,title,caption FROM social_content WHERE id=? FOR UPDATE", contentId);
    if (rows.isEmpty()) return;
    var upload = rows.getFirst();
    if (!VideoTitles.isVideo((String) upload.get("content_type"))) return;
    if (!jdbc.queryForList("SELECT video_id FROM application_video_uploads WHERE content_id=? FOR UPDATE", contentId).isEmpty())
      return;
    // Respect explicit unlinks and movie classifications on subsequent syncs/backfills.
    if (!jdbc.queryForList("SELECT id FROM application_video_assignment_audit WHERE content_id=? LIMIT 1 FOR UPDATE", contentId).isEmpty()
        || !jdbc.queryForList("SELECT content_id FROM social_post_reviews WHERE content_id=? AND topic='MOVIE' FOR UPDATE", contentId).isEmpty())
      return;
    String title = VideoTitles.displayTitle((String) upload.get("title"), (String) upload.get("caption"));
    if (title == null || title.codePointCount(0, title.length()) > 255) return;
    String videoId = (String) create(title, null).get("id");
    jdbc.update("INSERT INTO application_video_uploads(content_id,video_id,assigned_at) VALUES(?,?,UTC_TIMESTAMP(6))",
        contentId, videoId);
    audit(contentId, null, videoId);
  }

  public List<Map<String, Object>> list(int offset, int limit) {
    return jdbc.queryForList(
        "SELECT * FROM application_videos ORDER BY created_at,id LIMIT ? OFFSET ?", limit, offset);
  }

  public Map<String, Object> video(String id) {
    var rows = jdbc.queryForList("SELECT * FROM application_videos WHERE id=?", id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application video not found");
    return rows.getFirst();
  }

  public List<Map<String, Object>> uploads(String id) {
    video(id);
    return jdbc.queryForList(
        "SELECT c.id,c.platform,c.account_id,c.external_content_id,c.title,c.permalink,c.thumbnail_url,c.engagement_metadata,c.retrieved_at FROM social_content c JOIN application_video_uploads u ON u.content_id=c.id WHERE u.video_id=? ORDER BY c.id",
        id);
  }

  public List<Map<String, Object>> unassigned(int offset, int limit) {
    return unassigned(offset, limit, null);
  }

  public List<Map<String, Object>> unassigned(int offset, int limit, String platform) {
    String sql = "SELECT c.id,c.platform,c.account_id,c.external_content_id,c.title,c.permalink,c.thumbnail_url FROM social_content c LEFT JOIN application_video_uploads u ON u.content_id=c.id WHERE u.content_id IS NULL AND c.content_type IN ('VIDEO','SHORT_VIDEO','REEL','LIVE')";
    var args = new ArrayList<Object>();
    if (platform != null) {
      String normalized = platform.trim().toUpperCase(Locale.ROOT);
      if (!Set.of("INSTAGRAM", "YOUTUBE", "FACEBOOK").contains(normalized))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform must be INSTAGRAM, YOUTUBE or FACEBOOK");
      sql += " AND c.platform=?";
      args.add(normalized.toLowerCase(Locale.ROOT));
    }
    args.add(limit);
    args.add(offset);
    return jdbc.queryForList(sql + " ORDER BY c.id LIMIT ? OFFSET ?", args.toArray());
  }

  @Transactional
  public void assign(String video, long content, boolean reassign) {
    video(video);
    lockContent(content);
    var old =
        jdbc.queryForList(
            "SELECT video_id FROM application_video_uploads WHERE content_id=?",
            String.class,
            content);
    if (!old.isEmpty() && old.getFirst().equals(video)) return;
    if (!old.isEmpty() && !reassign)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Upload already assigned; use explicit reassignment");
    jdbc.update(
        "INSERT INTO application_video_uploads(content_id,video_id,assigned_at) VALUES(?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE video_id=VALUES(video_id),assigned_at=VALUES(assigned_at)",
        content,
        video);
    audit(content, old.isEmpty() ? null : old.getFirst(), video);
  }

  @Transactional
  public void unlink(String video, long content) {
    video(video);
    lockContent(content);
    if (jdbc.update(
            "DELETE FROM application_video_uploads WHERE content_id=? AND video_id=?",
            content,
            video)
        > 0) audit(content, video, null);
  }

  private void lockContent(long content) {
    var rows =
        jdbc.queryForList(
            "SELECT content_type FROM social_content WHERE id=? FOR UPDATE", String.class, content);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found");
    if (!Set.of("VIDEO", "SHORT_VIDEO", "REEL", "LIVE").contains(rows.getFirst()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content must be a video upload");
  }

  private void audit(long c, String old, String next) {
    jdbc.update(
        "INSERT INTO application_video_assignment_audit(content_id,old_video_id,new_video_id,changed_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",
        c,
        old,
        next);
  }

  // Upload links consolidate platform comments; audience assignments do not restrict reads.
  public List<Map<String, Object>> comments(String id, long after, int limit) {
    video(id);
    return jdbc.queryForList(
        "SELECT m.*,c.platform,c.account_id,c.external_content_id,c.permalink FROM social_comments m JOIN social_content c ON c.id=m.content_id JOIN application_video_uploads u ON u.content_id=c.id WHERE u.video_id=? AND m.id>? ORDER BY m.id LIMIT ?",
        id,
        after,
        limit);
  }

  public List<Map<String, Object>> status(String id) {
    video(id);
    return jdbc.queryForList(
        "SELECT t.* FROM social_comment_tasks t JOIN application_video_uploads u ON u.content_id=t.content_id WHERE u.video_id=? ORDER BY t.id",
        id);
  }

  public List<Map<String, Object>> audit(String id, int offset, int limit) {
    video(id);
    return jdbc.queryForList(
        "SELECT * FROM application_video_assignment_audit WHERE old_video_id=? OR new_video_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
        id,
        id,
        limit,
        offset);
  }
}
