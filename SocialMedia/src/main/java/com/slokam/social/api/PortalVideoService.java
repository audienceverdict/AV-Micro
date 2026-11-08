package com.slokam.social.api;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortalVideoService {
  private final JdbcTemplate jdbc;
  public PortalVideoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Page(List<Map<String, Object>> items, int page, int size, long totalElements,
      long totalPages, boolean hasNext) {}

  @Transactional(readOnly = true)
  public Page list(int page, int size, String platform) {
    return list(page, size, platform, null, null);
  }

  @Transactional(readOnly = true)
  public Page list(int page, int size, String platform, String topic, String categoryName) {
    if (page < 0 || page > 1000000 || size < 1 || size > 200)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
    String where = " FROM application_videos v WHERE 1=1";
    var args = new ArrayList<Object>();
    String filters = "";
    if (platform != null) {
      String normalized = platform.trim().toLowerCase(Locale.ROOT);
      if (!Set.of("instagram", "facebook", "youtube", "linkedin").contains(normalized))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported platform");
      filters += " AND c.platform=?";
      args.add(normalized);
    }
    if (topic != null) {
      if (!Set.of("MOVIE", "AV").contains(topic))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Topic must be MOVIE or AV");
      filters += " AND r.topic=?";
      args.add(topic);
    }
    if (categoryName != null) {
      filters += " AND r.category_name=?";
      args.add(categoryName.trim());
    }
    if (!filters.isEmpty())
      where += " AND EXISTS (SELECT 1 FROM application_video_uploads u"
          + " JOIN social_content c ON c.id=u.content_id"
          + " LEFT JOIN social_post_reviews r ON r.content_id=c.id"
          + " WHERE u.video_id=v.id" + filters + ")";
    Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    // Match every master field returned by the admin video list; enrich below for display.
    var items = jdbc.queryForList("SELECT v.*" + where
        + " ORDER BY v.created_at,v.id LIMIT ? OFFSET ?", args.toArray());
    if (!items.isEmpty()) {
      var ids = items.stream().map(row -> row.get("id")).toList();
      String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
      var uploads = jdbc.queryForList("SELECT u.video_id AS videoId,c.id,c.platform,"
          + "a.account_name AS accountName,c.content_type AS contentType,c.title,c.caption,c.permalink,"
          + "r.topic,r.category_name AS categoryName,c.thumbnail_url AS thumbnailUrl,c.published_at AS publishedAt,"
          + "c.engagement_metadata,c.retrieved_at FROM application_video_uploads u"
          + " JOIN social_content c ON c.id=u.content_id"
          + " LEFT JOIN social_accounts a ON a.id=c.account_id"
          + " LEFT JOIN social_post_reviews r ON r.content_id=c.id"
          + " WHERE u.video_id IN (" + placeholders + ") ORDER BY c.id", ids.toArray());
      PortalMetrics.enrich(jdbc, uploads);
      if (!uploads.isEmpty()) {
        var contentIds = uploads.stream().map(row -> row.get("id")).toList();
        String contentPlaceholders = String.join(",", Collections.nCopies(contentIds.size(), "?"));
        var assets = jdbc.queryForList("SELECT content_id AS contentId,media_type AS mediaType,"
            + "media_url AS mediaUrl,thumbnail_url AS thumbnailUrl,width,height,"
            + "duration_seconds AS durationSeconds,display_order AS displayOrder"
            + " FROM social_media_assets WHERE content_id IN (" + contentPlaceholders + ")"
            + " ORDER BY content_id,display_order,id", contentIds.toArray());
        for (var upload : uploads)
          upload.put("assets", assets.stream().filter(asset -> Objects.equals(asset.get("contentId"), upload.get("id")))
              .map(asset -> { var copy = new LinkedHashMap<>(asset); copy.remove("contentId"); return copy; }).toList());
      }
      for (var item : items)
        item.put("uploads", uploads.stream().filter(upload -> Objects.equals(upload.get("videoId"), item.get("id")))
            .map(upload -> { var copy = new LinkedHashMap<>(upload); copy.remove("videoId"); return copy; }).toList());
    }
    long count = total == null ? 0 : total;
    return new Page(items, page, size, count, (count + size - 1) / size, ((long) page + 1) * size < count);
  }
}
