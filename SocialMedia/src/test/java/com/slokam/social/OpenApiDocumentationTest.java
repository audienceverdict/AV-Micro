package com.slokam.social;

import com.slokam.social.api.*;
import com.slokam.social.comments.CommentStore;
import com.slokam.social.configuration.OpenApiConfiguration;
import com.slokam.social.publishing.*;
import com.slokam.social.replies.*;
import com.slokam.social.statistics.StatisticsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(classes = OpenApiDocumentationTest.TestApplication.class,
    properties = {"spring.config.location=optional:classpath:/openapi-test.yml", "springdoc.paths-to-match=/api/**", "spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
@AutoConfigureMockMvc(addFilters = false)
class OpenApiDocumentationTest {
  @Configuration
  @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
  @Import({OpenApiConfiguration.class, VideoController.class, StatisticsController.class,
      PublishingController.class, ReplyController.class})
  static class TestApplication {}

  @MockitoBean VideoService videos;
  @MockitoBean CommentStore comments;
  @MockitoBean JdbcTemplate jdbc;
  @MockitoBean PublishingService publishing;
  @MockitoBean PublishingMediaStore media;
  @MockitoBean ReplyService replies;
  @MockitoBean ReplyAccess access;
  @Autowired MockMvc mvc;

  @Test
  void documentsAllRoutesWithSchemasStatusesAndScopedAuthentication() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths.*", hasSize(29)))
        .andExpect(jsonPath("$.info.title").value("Social Media Operator API"))
        .andExpect(jsonPath("$.paths['/api/comments'].get.security[0].identityBearer").isArray())
        .andExpect(jsonPath("$.paths['/api/videos'].get.security").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/videos'].post.responses['201'].content['application/json'].schema['$ref']").value("#/components/schemas/ApplicationVideo"))
        .andExpect(jsonPath("$.paths['/api/content'].post.requestBody.content['multipart/form-data']").exists())
        .andExpect(jsonPath("$.paths['/api/content'].post.responses['413']").exists())
        .andExpect(jsonPath("$.paths['/api/comments/{id}/replies'].post.responses['202']").exists())
        .andExpect(jsonPath("$.paths['/api/videos/{videoId}/uploads/{contentId}'].put.responses['204']").exists())
        .andExpect(jsonPath("$.paths['/api/content/{id}/media'].get.responses['200'].content['video/mp4'].schema.format").value("binary"))
        .andExpect(jsonPath("$.components.schemas.SavedContent.properties.publications.items['$ref']").value("#/components/schemas/Publication"));
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
  }
}
