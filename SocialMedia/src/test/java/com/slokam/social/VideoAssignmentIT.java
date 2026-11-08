package com.slokam.social;

import com.slokam.social.assignments.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker=true)
class VideoAssignmentIT {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
  @Test void migrationBulkIdempotenceIsolationAndRollback() throws Exception {
    var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var jdbc = new JdbcTemplate(ds);
    jdbc.update("INSERT INTO application_videos(id,title,created_at,updated_at) VALUES('VID-1','One',NOW(),NOW()),('VID-2','Two',NOW(),NOW())");
    var directory = mock(AudienceDirectory.class);
    var service = new VideoAssignmentService(jdbc,directory);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    var admin = new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    var audience = new UsernamePasswordAuthenticationToken("one","",List.of(new SimpleGrantedAuthority("ROLE_USER")));
    var videos = List.of("VID-1","VID-2"); var recipients = List.of("one","two");
    assertEquals(4,tx.execute(s -> service.assign(videos,recipients,admin,false)).created());
    assertEquals(4,tx.execute(s -> service.assign(videos,recipients,admin,false)).alreadyAssigned());
    jdbc.update("INSERT INTO social_accounts(id,platform,external_account_id,enabled,created_at,updated_at) VALUES(1,'youtube','channel',1,NOW(),NOW())");
    jdbc.update("INSERT INTO social_content(id,account_id,platform,external_content_id,content_type,content_status,published_at,created_at,updated_at,retrieved_at,engagement_metadata) VALUES(1,1,'youtube','external-video','VIDEO','AVAILABLE',NOW(),NOW(),NOW(),NOW(),?)",
        "{\"viewCount\":\"3\",\"likeCount\":\"0\",\"commentCount\":\"1\"}");
    jdbc.update("INSERT INTO application_video_uploads(video_id,content_id,assigned_at) VALUES('VID-1',1,NOW())");
    var linked = service.list(audience,"one","VID-1",null,null,0,20).items().getFirst().uploads().getFirst();
    assertEquals(3L,linked.viewCount());
    assertEquals(0L,linked.likeCount());
    assertEquals(1L,linked.commentCount());
    assertEquals(2,service.list(audience,"one",null,null,null,0,1).totalElements());
    assertTrue(service.list(audience,"one",null,null,null,0,1).hasNext());
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> service.list(audience,"two",null,null,null,0,20));
    assertEquals(1,tx.execute(s -> service.assign(List.of("VID-1"),List.of(),audience,true)).alreadyAssigned());
    assertEquals("admin",jdbc.queryForObject("SELECT assigned_by FROM video_user_assignments WHERE video_id='VID-1' AND user_id='one'",String.class));
    assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
      service.assign(videos,List.of("rollback"),admin,false);
      throw new IllegalStateException("force rollback");
    }));
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM video_user_assignments WHERE user_id='rollback'",Integer.class));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> tx.execute(s -> service.assign(videos,List.of("concurrent"),admin,false)));
      var second = pool.submit(() -> tx.execute(s -> service.assign(videos,List.of("concurrent"),admin,false)));
      assertEquals(2,first.get(20,TimeUnit.SECONDS).created()+second.get(20,TimeUnit.SECONDS).created());
    }
    assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
      service.unassign(videos, recipients, admin, false);
      throw new IllegalStateException("force unassign rollback");
    }));
    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM video_user_assignments WHERE user_id IN ('one','two')", Integer.class));
    assertEquals(2, tx.execute(s -> service.unassign(videos, List.of("two"), audience, true)).removed());
    assertEquals(2, tx.execute(s -> service.unassign(videos, List.of(), audience, true)).alreadyUnassigned());
    assertEquals(2, service.list(admin, "two", null, null, null, 0, 20).totalElements());
    assertEquals(2, tx.execute(s -> service.unassign(videos, List.of("two", "two"), admin, false)).removed());
    assertEquals(2, tx.execute(s -> service.unassign(videos, List.of("two"), admin, false)).alreadyUnassigned());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM application_videos", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM application_video_uploads", Integer.class));
  }
}
