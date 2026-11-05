package com.slokam.av.config;
import com.slokam.av.controller.InternalShowController;
import com.slokam.av.repository.ShowRepository;
import com.slokam.av.security.*;
import com.slokam.av.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(controllers=InternalShowController.class,properties="app.cors-origins=http://localhost:5173")
@Import({SecurityConfig.class,InternalSecurityConfig.class,JwtAuthenticationFilter.class})
class InternalSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean ShowRepository shows;
 @MockitoBean JwtService jwt;
 @MockitoBean CustomUserDetailsService users;
 @Test void internalGetRequiresServiceCredentialEvenThoughPublicGetsAreAllowed() throws Exception {
  mvc.perform(get("/internal/shows/show/exists")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/shows/show/exists").header("X-Service-Token","wrong")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/shows/show/exists").header("X-Service-Token","test-service-token-at-least-thirty-two-characters")).andExpect(status().isOk());
 }
}
