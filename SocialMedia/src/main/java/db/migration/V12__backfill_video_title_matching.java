package db.migration;

import com.slokam.social.api.VideoService;
import com.slokam.social.application.VideoTitles;
import java.util.HashSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class V12__backfill_video_title_matching extends BaseJavaMigration {
  private static final Logger log = LoggerFactory.getLogger(V12__backfill_video_title_matching.class);

  @Override
  public void migrate(Context context) {
    var jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
    var keys = new HashSet<String>();
    for (var video : jdbc.queryForList("SELECT id,title FROM application_videos ORDER BY created_at,id")) {
      String key = VideoTitles.key((String) video.get("title"));
      if (key == null) continue;
      if (keys.add(key)) {
        jdbc.update("UPDATE application_videos SET title_match_key=? WHERE id=?", key, video.get("id"));
      } else {
        log.warn("Legacy application video {} has a duplicate normalized title; preserving its ID and assignments. Future matches use the oldest video.", video.get("id"));
      }
    }
    // Keyset pagination avoids loading the entire social-content library into memory.
    var videos = new VideoService(jdbc);
    long after = 0;
    while (true) {
      var ids = jdbc.queryForList("SELECT c.id FROM social_content c LEFT JOIN application_video_uploads u ON u.content_id=c.id"
          + " WHERE c.id>? AND u.content_id IS NULL AND c.content_type IN ('VIDEO','SHORT_VIDEO','REEL','LIVE')"
          + " ORDER BY c.id LIMIT 200", Long.class, after);
      if (ids.isEmpty()) break;
      ids.forEach(videos::autoAssign);
      after = ids.getLast();
    }
  }
}
