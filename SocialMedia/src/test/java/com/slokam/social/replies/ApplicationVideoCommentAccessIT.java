package com.slokam.social.replies;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import com.slokam.social.api.*;
import com.slokam.social.comments.CommentStore;
import com.slokam.social.configuration.SecurityConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes=ApplicationVideoCommentAccessIT.Application.class, properties={
    "spring.config.location=optional:classpath:/security-test.yml",
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "jwt.jwk-set-uri=http://localhost/unused"})
@AutoConfigureMockMvc
class ApplicationVideoCommentAccessIT {
  @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
  @Configuration
  @EnableAutoConfiguration(exclude=DataSourceAutoConfiguration.class)
  @Import({SecurityConfiguration.class,VideoController.class,VideoService.class,
      ReplyController.class,ReplyService.class,ReplyAccess.class,ReplyProperties.class})
  static class Application {
    @Bean JdbcTemplate jdbc() {
      var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
      Flyway.configure().dataSource(ds).load().migrate();
      return new JdbcTemplate(ds);
    }
    @Bean TransactionTemplate tx(JdbcTemplate jdbc) {
      return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
  }
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ReplyProperties permissions;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean CommentStore commentStore;

  @Test void unassignedUsersReadAllLinkedPlatformsAndAdminsWriteAcrossAllAccounts() throws Exception {
    jdbc.update("INSERT INTO application_videos(id,title,created_at,updated_at) VALUES('VID-all','All platforms',NOW(),NOW()),('VID-empty','Empty',NOW(),NOW())");
    jdbc.update("INSERT INTO video_user_assignments(id,video_id,user_id,assigned_by,assignment_source) VALUES('assignment','VID-all','someone-else','admin','ADMIN')");
    int id=0;
    for(String platform:List.of("youtube","facebook","instagram")) {
      id++;
      jdbc.update("INSERT INTO social_accounts(id,platform,external_account_id,enabled,created_at,updated_at) VALUES(?,?,?,1,NOW(),NOW())",id,platform,"account-"+id);
      jdbc.update("INSERT INTO social_content(id,account_id,platform,external_content_id,created_at,updated_at) VALUES(?,?,?,?,NOW(),NOW())",id,id,platform,"upload-"+id);
      jdbc.update("INSERT INTO application_video_uploads(content_id,video_id,assigned_at) VALUES(?,'VID-all',NOW())",id);
      jdbc.update("INSERT INTO social_comments(id,content_id,external_comment_id,comment_text,retrieved_at) VALUES(?,?,?,?,NOW())",id,id,"comment-"+id,platform+" comment");
    }
    // A linked video is not the same as a video assigned to the requesting user.
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM video_user_assignments WHERE user_id='reader'",Integer.class));
    var user=jwt().jwt(j -> j.subject("reader")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    mvc.perform(get("/api/videos/VID-all/comments")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/videos/VID-all/comments").with(user))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].platform").value("youtube"))
        .andExpect(jsonPath("$[1].platform").value("facebook"))
        .andExpect(jsonPath("$[2].platform").value("instagram"));
    mvc.perform(get("/api/videos/VID-all/comments").param("afterId","1").param("limit","1").with(user))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].external_comment_id").value("comment-2"));
    mvc.perform(get("/api/videos/VID-empty/comments").with(user))
        .andExpect(status().isOk()).andExpect(content().json("[]"));
    mvc.perform(get("/api/videos/VID-missing/comments").with(user)).andExpect(status().isNotFound());
    var admin=jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    assertTrue(permissions.getOperators().isEmpty());
    mvc.perform(get("/api/comments").with(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    for(int comment=1;comment<=3;comment++) {
      mvc.perform(post("/api/comments/"+comment+"/replies").with(user)
          .header("Idempotency-Key","user-"+comment).contentType("application/json").content("{\"text\":\"Hello\"}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(post("/api/comments/2/replies").with(admin)
        .header("Idempotency-Key","admin-other-account").contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isAccepted());
    mvc.perform(post("/api/comments/1/replies").with(admin)
        .header("Idempotency-Key","allowed").contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"));
    mvc.perform(post("/api/uploads/1/comments").contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/uploads/1/comments").with(user).contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/uploads/2/comments").with(admin).contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isAccepted());
    mvc.perform(post("/api/uploads/1/comments").with(admin).contentType("application/json").content("{\"text\":\" \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/uploads/999/comments").with(admin).contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/uploads/1/comments").with(admin).contentType("application/json").content("{\"text\":\"New comment\"}"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.content_id").value(1))
        .andExpect(jsonPath("$.status").value("QUEUED"));
    for(int attempt=0;attempt<2;attempt++) {
      mvc.perform(post("/api/uploads/1/comments").with(admin).header("Idempotency-Key","new-comment")
          .contentType("application/json").content("{\"text\":\"Repeat safely\"}"))
          .andExpect(status().isAccepted());
    }
    mvc.perform(post("/api/uploads/1/comments").with(admin).header("Idempotency-Key","new-comment")
        .contentType("application/json").content("{\"text\":\"Changed\"}"))
        .andExpect(status().isConflict());
    // Preserve the existing route's required idempotency header.
    mvc.perform(post("/api/content/1/comments").with(admin).contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isBadRequest());
    assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM social_comment_replies",Integer.class));

  }
}
