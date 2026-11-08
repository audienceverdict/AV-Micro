package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;

import com.slokam.social.api.VideoService;
import com.slokam.social.application.VideoTitles;
import java.util.List;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class VideoTitleMatchingIT {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
  DriverManagerDataSource ds;
  JdbcTemplate jdbc;
  VideoService videos;
  TransactionTemplate tx;

  @BeforeEach
  void setup() {
    ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    jdbc = new JdbcTemplate(ds);
    videos = new VideoService(jdbc);
    tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
  }

  void migrate() {
    Flyway.configure().dataSource(ds).load().migrate();
  }

  void upload(long id, String platform, String type, String title, String caption) {
    jdbc.update("INSERT INTO social_accounts(id,platform,external_account_id,enabled,created_at,updated_at) VALUES(?,?,?,1,NOW(),NOW())",
        id, platform, "account-" + id);
    jdbc.update("INSERT INTO social_content(id,account_id,platform,external_content_id,content_type,title,caption,content_status,published_at,created_at,updated_at,retrieved_at)"
        + " VALUES(?,?,?,?,?,?,?,'AVAILABLE',NOW(),NOW(),NOW(),NOW())", id, id, platform, "upload-" + id, type, title, caption);
  }

  long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  String assigned(long id) {
    return jdbc.queryForObject("SELECT video_id FROM application_video_uploads WHERE content_id=?", String.class, id);
  }

  @Test
  void crossPlatformUploadsReuseManualVideoAndPreserveAssignmentsAndUnlinks() {
    migrate();
    String id = tx.execute(s -> (String) videos.create("Antah karana", "original").get("id"));
    upload(1, "youtube", "VIDEO", "ANTAHKARANA", null);
    upload(2, "instagram", "REEL", null, "\nAntah     karana\nDescription #tag");
    upload(3, "facebook", "SHORT_VIDEO", null, "antah\tkarana");
    for (long content : List.of(1L, 2L, 3L)) tx.executeWithoutResult(s -> videos.autoAssign(content));
    assertEquals(1, count("application_videos"));
    assertEquals(3, count("application_video_uploads"));
    assertEquals(id, assigned(1));
    assertEquals(id, assigned(2));
    assertEquals(id, assigned(3));
    assertEquals(3, count("application_video_assignment_audit"));

    String reused = tx.execute(s -> (String) videos.create("  antahkarana ", "replacement").get("id"));
    assertEquals(id, reused);
    assertEquals("original", videos.video(id).get("description"));
    assertEquals("Antah karana", videos.video(id).get("title"));
    jdbc.update("UPDATE social_content SET title='Renamed to something else' WHERE id=1");
    tx.executeWithoutResult(s -> videos.autoAssign(1));
    assertEquals(id, assigned(1));
    assertEquals(1, count("application_videos"));
    tx.executeWithoutResult(s -> videos.unlink(id, 2));
    tx.executeWithoutResult(s -> videos.autoAssign(2));
    assertEquals(2, count("application_video_uploads"));
    assertEquals(4, count("application_video_assignment_audit"));
  }

  @Test
  void simultaneousNewUploadsAndManualCreationProduceOneId() throws Exception {
    migrate();
    upload(1, "youtube", "VIDEO", "HI Hello", null);
    upload(2, "instagram", "REEL", null, "hi     HELLO");
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var first = pool.submit(() -> { start.await(); tx.executeWithoutResult(s -> videos.autoAssign(1)); return assigned(1); });
      var second = pool.submit(() -> { start.await(); tx.executeWithoutResult(s -> videos.autoAssign(2)); return assigned(2); });
      var manual = pool.submit(() -> { start.await(); return tx.execute(s -> videos.create("HIHELLO", null).get("id")); });
      start.countDown();
      assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
      assertEquals(assigned(1), manual.get(20, TimeUnit.SECONDS));
    }
    assertEquals(1, count("application_videos"));
    assertEquals(2, count("application_video_assignment_audit"));
  }

  @Test
  void ignoresNonVideosMissingAndOverlongTitlesAndRollsBackAssignmentWithPage() {
    migrate();
    upload(1, "youtube", "IMAGE", "Not a video", null);
    upload(2, "instagram", "REEL", null, "\n\t\u00a0");
    upload(3, "facebook", "VIDEO", "x".repeat(256), null);
    for (long id : List.of(1L, 2L, 3L)) tx.executeWithoutResult(s -> videos.autoAssign(id));
    assertEquals(0, count("application_videos"));
    upload(4, "youtube", "LIVE", "Rollback", null);
    assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
      videos.autoAssign(4);
      throw new IllegalStateException("page failed");
    }));
    assertEquals(0, count("application_videos"));
    assertEquals(0, count("application_video_uploads"));
    assertEquals(0, count("application_video_assignment_audit"));
  }

  @Test
  void upgradeKeepsLegacyIdsAndAssignmentsAndBackfillsUnassignedUploads() {
    Flyway.configure().dataSource(ds).target("10").load().migrate();
    jdbc.update("INSERT INTO application_videos(id,title,created_at,updated_at) VALUES"
        + "('VID-old','Antah karana','2020-01-01','2020-01-01'),('VID-duplicate','ANTAHKARANA','2021-01-01','2021-01-01')");
    upload(1, "youtube", "VIDEO", "antahkarana", null);
    upload(2, "instagram", "REEL", null, "Antah    karana");
    upload(3, "facebook", "VIDEO", "New title", null);
    upload(4, "youtube", "VIDEO", "Manual unlink", null);
    upload(5, "youtube", "VIDEO", "Movie", null);
    jdbc.update("INSERT INTO application_video_uploads(content_id,video_id,assigned_at) VALUES(1,'VID-duplicate',NOW())");
    jdbc.update("INSERT INTO application_video_assignment_audit(content_id,old_video_id,new_video_id,changed_at) VALUES(4,'VID-old',NULL,NOW())");
    jdbc.update("INSERT INTO social_post_reviews(content_id,status,topic,category_name,reviewed_by,reviewed_at) VALUES(5,'APPROVED','MOVIE','Movie','admin',NOW())");
    migrate();
    assertEquals(3, count("application_videos"));
    assertEquals("VID-duplicate", assigned(1));
    assertEquals("VID-old", assigned(2));
    assertNotNull(assigned(3));
    assertEquals(3, count("application_video_uploads"));
    assertEquals(3, count("application_video_assignment_audit"));
    assertEquals(VideoTitles.key("Antahkarana"), videos.video("VID-old").get("title_match_key"));
    assertNull(videos.video("VID-duplicate").get("title_match_key"));
    assertEquals("VID-old", tx.execute(s -> videos.create(" antah KARANA ", null).get("id")));
    migrate();
    assertEquals(3, count("application_videos"));
  }
}
