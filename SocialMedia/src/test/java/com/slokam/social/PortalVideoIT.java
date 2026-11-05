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

  @Test void feedFiltersVisibilityPaginatesAndOrdersAssets() {
    var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var jdbc = new JdbcTemplate(ds);
    jdbc.update("INSERT INTO social_accounts(id,platform,external_account_id,enabled,created_at,updated_at) VALUES (1,'youtube','a',1,NOW(),NOW()),(2,'youtube','b',0,NOW(),NOW())");
    for (int i=1; i<=6; i++) {
      jdbc.update("INSERT INTO social_content(id,account_id,platform,external_content_id,content_type,content_status,published_at,created_at,updated_at) VALUES (?,?,'youtube',?,?,?,DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? DAY),NOW(),NOW())",
          i,i==4?2:1,"post-"+i,i==5?"IMAGE":"VIDEO",i==3?"UNAVAILABLE":"AVAILABLE",i==6?1:-1);
    }
    jdbc.update("INSERT INTO social_media_assets(content_id,external_asset_id,media_type,media_url,display_order,created_at,updated_at) VALUES (2,'second','VIDEO','https://example.com/2.mp4',1,NOW(),NOW()),(2,'first','VIDEO','https://example.com/1.mp4',0,NOW(),NOW())");
    var service = new PortalVideoService(jdbc);
    var first = service.list(0,1," YOUTUBE ");
    assertEquals(2,first.totalElements());
    assertEquals(2,first.totalPages());
    assertTrue(first.hasNext());
    assertEquals(2L,((Number)first.items().getFirst().get("id")).longValue());
    var assets = (java.util.List<?>) first.items().getFirst().get("assets");
    assertEquals("https://example.com/1.mp4",((java.util.Map<?,?>)assets.getFirst()).get("mediaUrl"));
    assertFalse(first.items().getFirst().containsKey("provider_metadata"));
    assertFalse(service.list(1,1,null).hasNext());
    assertTrue(service.list(10,1,null).items().isEmpty());
    assertEquals(0,service.list(0,20,"instagram").totalElements());
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.list(0,20,"bad"));
  }
}
