package com.slokam.social.configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(classes = SecurityConfigurationTest.Application.class, properties = {
    "spring.config.location=optional:classpath:/security-test.yml",
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "jwt.jwk-set-uri=http://localhost/unused"})
@AutoConfigureMockMvc
class SecurityConfigurationTest {
  @Configuration
  @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
  @Import({SecurityConfiguration.class, Endpoints.class})
  static class Application {}
  @RestController
  static class Endpoints {
    @GetMapping({"/api/videos", "/api/portal/videos", "/api/content/{id}/media"}) String get() { return "ok"; }
    @PostMapping("/api/content/{id}/media") String post() { return "ok"; }
  }
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @Test void operatorApisRequireIdentityAdminAndMediaExceptionIsGetOnly() throws Exception {
    mvc.perform(get("/api/videos")).andExpect(status().isUnauthorized());
    when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));
    mvc.perform(get("/api/videos").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    when(decoder.decode("user")).thenReturn(token("USER"));
    mvc.perform(get("/api/videos").header("Authorization", "Bearer user")).andExpect(status().isForbidden());
    when(decoder.decode("admin")).thenReturn(token("ADMIN"));
    mvc.perform(get("/api/videos").header("Authorization", "Bearer admin")).andExpect(status().isOk());
    mvc.perform(get("/api/content/opaque/media")).andExpect(status().isOk());
    mvc.perform(post("/api/content/opaque/media")).andExpect(status().isUnauthorized());
  }
  @Test void portalFeedRequiresAuthenticationAndAllowsRegularUsers() throws Exception {
    mvc.perform(get("/api/portal/videos")).andExpect(status().isUnauthorized());
    when(decoder.decode("user")).thenReturn(token("USER"));
    mvc.perform(get("/api/portal/videos").header("Authorization", "Bearer user")).andExpect(status().isOk());
    mvc.perform(post("/api/portal/videos").header("Authorization", "Bearer user")).andExpect(status().isForbidden());
  }

  private Jwt token(String role) {
    return Jwt.withTokenValue(role).header("alg", "RS256").subject("identity-user")
        .claim("roles", List.of(role)).build();
  }
}
