package com.slokam.social;

import com.slokam.social.assignments.*;
import com.slokam.social.configuration.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=VideoAssignmentTest.Application.class, properties={
    "spring.config.location=optional:classpath:/assignment-test.yml", "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false", "jwt.jwk-set-uri=http://localhost/unused"})
@AutoConfigureMockMvc
class VideoAssignmentTest {
  @Configuration @EnableAutoConfiguration(exclude=DataSourceAutoConfiguration.class)
  @Import({VideoAssignmentController.class, VideoAssignmentService.class, com.slokam.social.api.PortalVideoController.class, com.slokam.social.api.PortalVideoService.class, SecurityConfiguration.class, OpenApiConfiguration.class})
  static class Application {}
  @Autowired MockMvc mvc;
  @Autowired VideoAssignmentService service;
  @MockitoBean JdbcTemplate jdbc;
  @MockitoBean AudienceDirectory users;
  @MockitoBean JwtDecoder decoder;
  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor as(String role) {
    return jwt().jwt(j -> j.subject("me")).authorities(new SimpleGrantedAuthority("ROLE_"+role));
  }
  @Test void audienceCannotAssignOthersOrReadGlobalRecipients() throws Exception {
    mvc.perform(post("/api/video-assignments").with(as("USER")).contentType("application/json")
        .content("{\"videoIds\":[\"VID-1\"],\"userIds\":[\"other\"]}")).andExpect(status().isForbidden());
    mvc.perform(get("/api/video-assignments").with(as("USER"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/videos/VID-1/assigned-users").with(as("USER"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/users/other/video-assignments").with(as("USER"))).andExpect(status().isForbidden());
    verifyNoInteractions(jdbc, users);
  }
  @Test void selfAssignmentUsesSubjectAndDuplicateIsNoOp() throws Exception {
    when(jdbc.queryForObject(anyString(),eq(Long.class),eq("VID-1"))).thenReturn(1L);
    when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new org.springframework.dao.DuplicateKeyException("existing"));
    mvc.perform(post("/api/me/video-assignments").with(as("USER")).contentType("application/json")
        .content("{\"videoIds\":[\"VID-1\"],\"userIds\":[\"other\"]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.alreadyAssigned").value(1));
    verify(users).requireEnabled("me");
    verify(users,never()).requireEnabled("other");
    verify(jdbc).update(anyString(), anyString(), eq("VID-1"), eq("me"), eq("me"), eq("SELF"));
  }
  @Test void managersAndAdminsCanBulkAssignAndReadAll() throws Exception {
    for (String role : List.of("ADMIN","SOCIAL_MEDIA_MANAGER")) {
      reset(jdbc,users);
      when(jdbc.queryForObject(anyString(),eq(Long.class),eq("VID-1"))).thenReturn(1L);
      when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
      mvc.perform(post("/api/video-assignments").with(as(role)).contentType("application/json")
          .content("{\"videoIds\":[\"VID-1\",\"VID-1\"],\"userIds\":[\"one\",\"two\"]}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));
      verify(users).requireEnabled("one"); verify(users).requireEnabled("two");
      when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
      mvc.perform(get("/api/video-assignments").with(as(role))).andExpect(status().isOk());
      mvc.perform(get("/api/videos/VID-1/assigned-users").with(as(role))).andExpect(status().isOk());
    }
  }
  @Test void invalidVideoOrRecipientWritesNothing() throws Exception {
    when(jdbc.queryForObject(anyString(),eq(Long.class),eq("missing"))).thenReturn(0L);
    mvc.perform(post("/api/me/video-assignments").with(as("USER")).contentType("application/json")
        .content("{\"videoIds\":[\"missing\"]}")).andExpect(status().isNotFound());
    when(jdbc.queryForObject(anyString(),eq(Long.class),eq("VID-1"))).thenReturn(1L);
    doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
        .when(users).requireEnabled("bad");
    mvc.perform(post("/api/video-assignments").with(as("ADMIN")).contentType("application/json")
        .content("{\"videoIds\":[\"VID-1\"],\"userIds\":[\"one\",\"bad\"]}"))
        .andExpect(status().isServiceUnavailable());
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
  @Test void ownListingIsScopedAndPaginationValidated() throws Exception {
    when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
    mvc.perform(get("/api/me/video-assignments").with(as("USER")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    verify(jdbc).queryForObject(contains("a.user_id=?"),eq(Long.class),eq("me"));
    mvc.perform(get("/api/me/video-assignments?size=101").with(as("USER"))).andExpect(status().isBadRequest());
    mvc.perform(get("/api/me/video-assignments")).andExpect(status().isUnauthorized());
  }
  @Test void selfUnassignUsesSubjectAndCountsDistinctMissingAssignments() throws Exception {
    when(jdbc.update(anyString(), eq("VID-1"), eq("me"))).thenReturn(1);
    mvc.perform(delete("/api/me/video-assignments").with(as("USER")).contentType("application/json")
        .content("{\"videoIds\":[\"VID-1\",\"VID-1\",\"missing\"],\"userIds\":[\"other\"]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.removed").value(1))
        .andExpect(jsonPath("$.alreadyUnassigned").value(1));
    verify(jdbc).update("DELETE FROM video_user_assignments WHERE video_id=? AND user_id=?", "VID-1", "me");
    verify(jdbc).update("DELETE FROM video_user_assignments WHERE video_id=? AND user_id=?", "missing", "me");
    verifyNoMoreInteractions(jdbc);
    verifyNoInteractions(users);
  }
  @Test void onlyAdminCanUnassignOtherUsers() throws Exception {
    String body = "{\"videoIds\":[\"VID-1\"],\"userIds\":[\"one\",\"two\",\"two\"]}";
    for (String role : List.of("USER", "SOCIAL_MEDIA_MANAGER")) {
      mvc.perform(delete("/api/video-assignments").with(as(role)).contentType("application/json").content(body))
          .andExpect(status().isForbidden());
      var actor = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
          "me", "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
      assertThrows(org.springframework.web.server.ResponseStatusException.class,
          () -> service.unassign(List.of("VID-1"), List.of("other"), actor, false));
    }
    verifyNoInteractions(jdbc, users);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    mvc.perform(delete("/api/video-assignments").with(as("ADMIN")).contentType("application/json").content(body))
        .andExpect(status().isOk()).andExpect(jsonPath("$.removed").value(2))
        .andExpect(jsonPath("$.alreadyUnassigned").value(0));
    verify(jdbc).update(anyString(), eq("VID-1"), eq("one"));
    verify(jdbc).update(anyString(), eq("VID-1"), eq("two"));
    verifyNoInteractions(users);
  }
  @Test void unassignRequiresAuthenticationAndValidNonemptySelections() throws Exception {
    mvc.perform(delete("/api/me/video-assignments").contentType("application/json")
        .content("{\"videoIds\":[\"VID-1\"]}")).andExpect(status().isUnauthorized());
    for (String body : List.of("{}", "{\"videoIds\":[]}", "{\"videoIds\":[\" \"]}",
        "{\"videoIds\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Collections.nCopies(51,"VID-1")) + "}"))
      mvc.perform(delete("/api/me/video-assignments").with(as("USER")).contentType("application/json").content(body))
          .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/video-assignments").with(as("ADMIN")).contentType("application/json")
        .content("{\"videoIds\":[\"VID-1\"],\"userIds\":[]}")).andExpect(status().isBadRequest());
    verifyNoInteractions(jdbc, users);
  }
  @Test void portalIsPublicAndValidatesPagination() throws Exception {
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    mvc.perform(get("/api/portal/videos?page=0&size=200"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/portal/videos?page=0&size=6").with(as("USER"))).andExpect(status().isOk());
    mvc.perform(get("/api/portal/videos?size=201")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/portal/videos?page=-1")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/videos")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/portal/videos'].get.security").isEmpty());
  }
  @Test void openApiDocumentsAllOperationsAndErrors() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/me/video-assignments'].post").exists())
        .andExpect(jsonPath("$.paths['/api/me/video-assignments'].delete").exists())
        .andExpect(jsonPath("$.paths['/api/video-assignments'].delete").exists())
        .andExpect(jsonPath("$.paths['/api/me/video-assignments'].get.responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/AudienceVideoAssignmentPage"))
        .andExpect(jsonPath("$.paths['/api/video-assignments'].post.responses['503']").exists())
        .andExpect(jsonPath("$.paths['/api/users/{userId}/video-assignments'].get.responses['403']").exists())
        .andExpect(jsonPath("$.paths['/api/videos/{videoId}/assigned-users'].get").exists());
  }
}
