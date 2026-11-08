package com.slokam.social.replies;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
class ReplyAccessTest {
  @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
  static void login(String role) {
    var jwt = Jwt.withTokenValue("test").header("alg","RS256").subject("alice").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority(role))));
  }
  @Test void identityAndAdminRoleAreRequiredButAccountAllowlistsAreNot() {
    var properties=new ReplyProperties();var operator=new ReplyProperties.Operator();
    operator.setAccountIds(List.of(4L));properties.getOperators().put("alice",operator);
    var access=new ReplyAccess(properties);var request=new MockHttpServletRequest();
    assertEquals(401,assertThrows(ResponseStatusException.class,()->access.authenticate(request)).getStatusCode().value());
    login("ROLE_USER");
    assertEquals(403,assertThrows(ResponseStatusException.class,()->access.authenticate(request)).getStatusCode().value());
    login("ROLE_ADMIN");
    var actor=access.authenticate(request);actor.require(4);assertEquals("alice",actor.name());
    actor.require(5);assertTrue(actor.allAccounts());
    properties.getOperators().clear();access.authenticate(request).require(9999);
  }
}
