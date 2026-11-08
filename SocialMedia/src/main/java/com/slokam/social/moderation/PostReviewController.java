package com.slokam.social.moderation;

import java.security.Principal;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/posts")
@Validated
public class PostReviewController {
  private final JdbcTemplate jdbc;
  public PostReviewController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "PENDING") String status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    if (!Set.of("PENDING", "APPROVED").contains(status))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be PENDING or APPROVED");
    String from = " FROM social_content c JOIN social_accounts a ON a.id=c.account_id"
        + " LEFT JOIN social_post_reviews r ON r.content_id=c.id"
        + " WHERE COALESCE(r.status,'PENDING')=?";
    long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, status);
    var items = jdbc.queryForList("SELECT c.id,c.title,c.caption,c.content_type AS contentType,"
        + "c.platform,a.account_name AS accountName,c.permalink,c.thumbnail_url AS thumbnailUrl,"
        + "c.published_at AS publishedAt,COALESCE(r.status,'PENDING') AS approvalStatus,"
        + "r.topic,r.category_name AS categoryName,r.reviewed_by AS reviewedBy,r.reviewed_at AS reviewedAt,"
        + "CASE WHEN EXISTS (SELECT 1 FROM social_publications p WHERE p.platform=c.platform"
        + " AND p.external_account_id=a.external_account_id AND p.external_content_id=c.external_content_id)"
        + " THEN 'APPLICATION' ELSE 'SOCIAL_MEDIA' END AS uploadSource" + from
        + " ORDER BY c.id DESC LIMIT ? OFFSET ?", status, size, (long) page * size);
    return Map.of("items", items, "page", page, "size", size, "totalElements", total,
        "totalPages", (total + size - 1) / size, "hasNext", ((long) page + 1) * size < total);
  }

  @PutMapping("/{contentId}/approval")
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> approve(@PathVariable @Positive long contentId,
      @RequestBody PostCategory category, Principal principal) {
    category.validate();
    if (jdbc.queryForList("SELECT id FROM social_content WHERE id=? FOR UPDATE", contentId).isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
    String type = jdbc.queryForObject("SELECT content_type FROM social_content WHERE id=?", String.class, contentId);
    var videos = new com.slokam.social.api.VideoService(jdbc);
    if (category.topic() == PostCategory.Topic.AV && Set.of("VIDEO", "SHORT_VIDEO", "REEL", "LIVE").contains(type == null ? "" : type)) {
      String video = (String) videos.create(category.name(), null).get("id");
      videos.assign(video, contentId, true);
    } else if (category.topic() == PostCategory.Topic.MOVIE) {
      for (String video : jdbc.queryForList("SELECT video_id FROM application_video_uploads WHERE content_id=?", String.class, contentId))
        videos.unlink(video, contentId);
    }
    jdbc.update("INSERT INTO social_post_reviews(content_id,status,topic,category_name,reviewed_by,reviewed_at)"
        + " VALUES(?,'APPROVED',?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE status='APPROVED',"
        + "topic=VALUES(topic),category_name=VALUES(category_name),reviewed_by=VALUES(reviewed_by),"
        + "reviewed_at=VALUES(reviewed_at)", contentId, category.topic().name(), category.name(), principal.getName());
    return jdbc.queryForMap("SELECT * FROM social_post_reviews WHERE content_id=?", contentId);
  }
}
