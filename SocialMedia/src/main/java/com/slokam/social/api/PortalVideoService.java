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
    if (page < 0 || page > 1000000 || size < 1 || size > 100)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
    String where = " FROM social_content c JOIN social_accounts a ON a.id=c.account_id"
        + " WHERE a.enabled=1 AND c.content_status='AVAILABLE'"
        + " AND c.content_type IN ('VIDEO','SHORT_VIDEO','REEL','LIVE')"
        + " AND c.published_at<=UTC_TIMESTAMP(6)";
    var args = new ArrayList<Object>();
    if (platform != null) {
      String normalized = platform.trim().toLowerCase(Locale.ROOT);
      if (!Set.of("instagram", "facebook", "youtube", "linkedin").contains(normalized))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported platform");
      where += " AND c.platform=?";
      args.add(normalized);
    }
    Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var items = jdbc.queryForList("SELECT c.id,c.platform,a.account_name AS accountName,"
        + "c.content_type AS contentType,c.title,c.caption,c.permalink,"
        + "c.thumbnail_url AS thumbnailUrl,c.published_at AS publishedAt" + where
        + " ORDER BY c.published_at DESC,c.id DESC LIMIT ? OFFSET ?", args.toArray());
    if (!items.isEmpty()) {
      var ids = items.stream().map(row -> row.get("id")).toList();
      String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
      var assets = jdbc.queryForList("SELECT content_id AS contentId,media_type AS mediaType,"
          + "media_url AS mediaUrl,thumbnail_url AS thumbnailUrl,width,height,"
          + "duration_seconds AS durationSeconds,display_order AS displayOrder"
          + " FROM social_media_assets WHERE content_id IN (" + placeholders + ")"
          + " ORDER BY content_id,display_order,id", ids.toArray());
      for (var item : items) {
        item.put("assets", assets.stream().filter(asset -> Objects.equals(asset.get("contentId"), item.get("id")))
            .map(asset -> { var copy = new LinkedHashMap<>(asset); copy.remove("contentId"); return copy; }).toList());
      }
    }
    long count = total == null ? 0 : total;
    return new Page(items, page, size, count, (count + size - 1) / size, ((long) page + 1) * size < count);
  }
}
