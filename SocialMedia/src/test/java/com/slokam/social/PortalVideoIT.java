package com.slokam.social;

import com.slokam.social.api.PortalVideoService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PortalVideoIT {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

  @Test void catalogIncludesAllMastersAndUploadsPaginatesAndOrdersAssets() {
    var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var jdbc = new JdbcTemplate(ds);
    jdbc.update("INSERT INTO social_accounts(id,platform,external_account_id,enabled,created_at,updated_at) VALUES (1,'youtube','a',1,NOW(),NOW()),(2,'youtube','b',0,NOW(),NOW())");
    for (int i=1; i<=6; i++) {
      jdbc.update("INSERT INTO social_content(id,account_id,platform,external_content_id,content_type,content_status,published_at,created_at,updated_at) VALUES (?,?,'youtube',?,?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? DAY),NOW(),NOW())",
          i,i==4?2:1,"post-"+i,i==5?"IMAGE":"VIDEO",i==3?"UNAVAILABLE":"AVAILABLE",i==6?1:-1);
    }
    jdbc.update("INSERT INTO social_media_assets(content_id,external_asset_id,media_type,media_url,display_order,created_at,updated_at) VALUES (2,'second','VIDEO','https://example.com/2.mp4',1,NOW(),NOW()),(2,'first','VIDEO','https://example.com/1.mp4',0,NOW(),NOW())");
    for (int i=1; i<=7; i++) {
      jdbc.update("INSERT INTO application_videos(id,title,created_at,updated_at) VALUES(?,?,NOW(),NOW())", "VID-"+i, "Video "+i);
      if (i<=6) jdbc.update("INSERT INTO application_video_uploads(video_id,content_id,assigned_at) VALUES(?,?,NOW())", "VID-"+i, i);
    }
    // Two uploads for one master must not duplicate it in the catalog.
    jdbc.update("UPDATE application_video_uploads SET video_id='VID-2' WHERE content_id=1");
    jdbc.update("INSERT INTO social_post_reviews(content_id,status,topic,category_name,reviewed_by,reviewed_at) VALUES(2,'APPROVED','AV','Example','admin',NOW())");
    var service = new PortalVideoService(jdbc);
    var all = service.list(0,200,null);
    assertEquals(7, all.totalElements());
    assertEquals(new com.slokam.social.api.VideoService(jdbc).list(0,200).stream().map(row -> row.get("id")).toList(),
        all.items().stream().map(row -> row.get("id")).toList());
    var admin = new com.slokam.social.api.VideoService(jdbc);
    assertEquals(admin.list(0,200), all.items().stream().map(row -> {
      var master = new java.util.LinkedHashMap<>(row);
      master.remove("uploads");
      return master;
    }).toList());
    // Compare complete master records on every page, including masters without uploads.
    for (int page=0; page<7; page++) {
      var publicPage = service.list(page,1,null);
      var master = new java.util.LinkedHashMap<>(publicPage.items().getFirst());
      master.remove("uploads");
      assertEquals(admin.list(page,1).getFirst(), master);
      assertEquals(page<6, publicPage.hasNext());
    }
    assertEquals(7, all.items().size()); // Includes disabled, unavailable, future and unreviewed uploads.
    assertTrue(((java.util.List<?>)all.items().getFirst().get("uploads")).isEmpty());
    assertTrue(((java.util.List<?>)all.items().getLast().get("uploads")).isEmpty());
    var first = service.list(0,1," YOUTUBE ");
    assertEquals(5,first.totalElements());
    assertEquals(5,first.totalPages());
    assertTrue(first.hasNext());
    assertEquals("VID-2",first.items().getFirst().get("id"));
    var uploads = (java.util.List<java.util.Map<String,Object>>)first.items().getFirst().get("uploads");
    assertEquals(2, uploads.size());
    var assets = (java.util.List<?>)uploads.get(1).get("assets");
    assertEquals("https://example.com/1.mp4",((java.util.Map<?,?>)assets.getFirst()).get("mediaUrl"));
    assertFalse(uploads.getFirst().containsKey("provider_metadata"));
    assertFalse(service.list(6,1,null).hasNext());
    assertTrue(service.list(10,1,null).items().isEmpty());
    assertEquals(0,service.list(0,20,"instagram").totalElements());
    assertEquals(1,service.list(0,20,null,"AV","Example").totalElements());
    assertEquals(0,service.list(0,20,null,"MOVIE",null).totalElements());
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.list(0,20,"bad"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.list(0,201,null));

    // Portal counts use local upload membership, preserve zero and retain known data after failure.
    jdbc.update("UPDATE social_content SET engagement_metadata=?,retrieved_at=TIMESTAMPADD(HOUR,-2,UTC_TIMESTAMP()) WHERE id=2",
        "{\"viewCount\":\"1\",\"likeCount\":\"9\"}");
    jdbc.update("INSERT INTO social_statistics_tasks(account_id,content_id,operation,due_at,status,last_success_at,result) VALUES (1,2,'public',UTC_TIMESTAMP(),'AUTH_REQUIRED',UTC_TIMESTAMP(),?)",
        "{\"pages\":[{\"items\":[{\"statistics\":{\"viewCount\":\"3\",\"likeCount\":\"0\",\"commentCount\":\"2\"}}]}]}");
    // Analytics period totals must never replace lifetime public counters.
    jdbc.update("INSERT INTO social_statistics_tasks(account_id,content_id,operation,due_at,status,last_success_at,result) VALUES (1,2,'analytics:views',UTC_TIMESTAMP(),'SUCCESS',UTC_TIMESTAMP(),?)",
        "{\"pages\":[{\"rows\":[[\"2026-11-01\",99]]}]}");
    var counted = ((java.util.List<java.util.Map<String,Object>>)service.list(0,1,"youtube").items().getFirst().get("uploads")).get(1);
    assertEquals(3L,counted.get("viewCount"));
    assertEquals(0L,counted.get("likeCount"));
    assertEquals(2L,counted.get("commentCount"));
    assertNotNull(counted.get("metricsUpdatedAt"));
    assertFalse(counted.containsKey("engagement_metadata"));
    assertNull(((java.util.List<java.util.Map<String,Object>>)service.list(1,1,"youtube").items().getFirst().get("uploads")).getFirst().get("viewCount"));

    jdbc.update("UPDATE social_content SET platform='instagram',engagement_metadata=?,retrieved_at=UTC_TIMESTAMP() WHERE id=1",
        "{\"like_count\":2,\"comments_count\":0}");
    var instagram = ((java.util.List<java.util.Map<String,Object>>)service.list(0,1,"instagram").items().getFirst().get("uploads")).getFirst();
    assertEquals(2L,instagram.get("likeCount"));
    assertEquals(0L,instagram.get("commentCount"));
    assertNull(instagram.get("viewCount"));
    assertNotNull(instagram.get("metricsUpdatedAt"));
  }
}
