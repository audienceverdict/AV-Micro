package com.slokam.social.replies;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.slokam.social.configuration.SocialProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class CommentPostingIT {
  @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
  @Test void postingAndReplyingPreserveScopeIdempotencyAndVisibleThreads() {
    var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
    // Upgrade an existing reply row as well as creating a fresh schema.
    Flyway.configure().dataSource(ds).target("8").load().migrate();
    var jdbc=new JdbcTemplate(ds);
    jdbc.update("INSERT INTO social_accounts(id,platform,external_account_id,enabled,created_at,updated_at) VALUES(1,'youtube','channel',1,NOW(),NOW())");
    jdbc.update("INSERT INTO social_content(id,account_id,platform,external_content_id,created_at,updated_at) VALUES(1,1,'youtube','video',NOW(),NOW())");
    jdbc.update("INSERT INTO social_comments(id,content_id,external_comment_id,comment_text,retrieved_at) VALUES(1,1,'parent','Original',NOW())");
    jdbc.update("INSERT INTO social_comment_replies(id,comment_id,request_key,reply_text,actor,status,created_at,updated_at) VALUES('legacy',1,'legacy','Thanks','admin','QUEUED',NOW(),NOW())");
    Flyway.configure().dataSource(ds).load().migrate();
    var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    var service=new ReplyService(jdbc,tx);
    var actor=new ReplyAccess.Actor("admin",List.of(1L));
    assertEquals(403,assertThrows(ResponseStatusException.class,()->service.enqueueComment(1,"blocked","Hi",new ReplyAccess.Actor("other",List.of()))).getStatusCode().value());
    var job=service.enqueueComment(1,"new","Hello",actor);
    assertEquals(job.get("id"),service.enqueueComment(1,"new","Hello",actor).get("id"));
    assertEquals(409,assertThrows(ResponseStatusException.class,()->service.enqueue(1,"new","Hello",actor)).getStatusCode().value());
    assertEquals(409,assertThrows(ResponseStatusException.class,()->service.enqueueComment(1,"legacy","Thanks",actor)).getStatusCode().value());
    var props=new SocialProperties();var platform=new SocialProperties.Platform();platform.setEnabled(true);
    var account=new SocialProperties.Account();account.setExternalId("channel");account.setEnabled(true);account.setCommentRepliesEnabled(false);
    platform.setAccounts(List.of(account));props.getPlatforms().put("youtube",platform);
    var settings=new ReplyProperties(); // No operator grants required for admin-authorized jobs.
    var provider=mock(CommentReplyProvider.class);when(provider.supports("youtube")).thenReturn(true);
    when(provider.send("youtube",account,"parent","Thanks")).thenReturn("reply-id");
    when(provider.post("youtube",account,"video","Hello")).thenReturn("new-id");
    var worker=new ReplyWorker(jdbc,tx,props,settings,List.of(provider));worker.run();worker.run();
    assertEquals("SENT",service.get((String)job.get("id"),actor).get("status"));
    assertEquals("SENT",service.get("legacy",actor).get("status"));
    assertNull(jdbc.queryForMap("SELECT * FROM social_comments WHERE external_comment_id='new-id'").get("parent_external_id"));
    assertEquals("parent",jdbc.queryForMap("SELECT * FROM social_comments WHERE external_comment_id='reply-id'").get("parent_external_id"));
    assertEquals(3,service.inbox(service.reader("user"),null,null,null,false,0,50).size());
    verify(provider,times(1)).post("youtube",account,"video","Hello");
    verify(provider,times(1)).send("youtube",account,"parent","Thanks");
  }
}
