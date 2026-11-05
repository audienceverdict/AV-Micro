package com.slokam.social;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.*;
import com.slokam.social.core.registry.PlatformRegistry;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.providers.linkedin.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class LinkedInExtensibilityTest {
  @Test
  void discoversNewPlatformAndFollowsOffsetPaginationWithRequiredHeaders() {
    var server = new WireMockServer(0);
    server.start();
    try (var context = new AnnotationConfigApplicationContext()) {
      var p = new SocialProperties();
      var platform = new SocialProperties.Platform();
      platform.setBaseUrl(server.baseUrl());
      platform.setApiVersion("202609");
      p.getPlatforms().put("linkedin", platform);
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "example", Map.of("social.platforms.linkedin.enabled", "true")));
      context.registerBean(SocialProperties.class, () -> p);
      context.registerBean(
          CredentialResolver.class,
          () ->
              new CredentialResolver(
                  new MockEnvironment().withProperty("TOKEN", "external-token")));
      context.registerBean(ProviderHttpClient.class, () -> new ProviderHttpClient(p));
      context.register(
          PlatformRegistry.class,
          LinkedInApiClient.class,
          LinkedInMapper.class,
          LinkedInClassifier.class,
          LinkedInAdapter.class,
          LinkedInComponentFactory.class);
      context.refresh();
      server.stubFor(
          get(urlPathEqualTo("/posts"))
              .withQueryParam("start", equalTo("0"))
              .willReturn(
                  okJson(
                      """
          {"elements":[{"id":"urn:li:share:1","author":"urn:li:organization:42","commentary":"Hello","content":{},"publishedAt":1791453540000,"lastModifiedAt":1791453540000}],"paging":{"start":0,"count":100,"links":[{"rel":"next","href":"https://api.linkedin.com/rest/posts?start=100&count=100"}]}}
          """)));
      server.stubFor(
          get(urlPathEqualTo("/posts"))
              .withQueryParam("start", equalTo("100"))
              .willReturn(
                  okJson(
                      """
          {"elements":[],"paging":{"start":100,"count":100,"links":[]}}
          """)));
      var adapter = context.getBean(PlatformRegistry.class).resolve("linkedin");
      var account =
          new SocialAccount(1L, "linkedin", "urn:li:organization:42", "Name", "TOKEN", false);
      var query =
          new ContentQuery(
              1L,
              null,
              Fixtures.NOW,
              100,
              null,
              Set.of(ContentType.TEXT, ContentType.UNKNOWN),
              SyncMode.INCREMENTAL);
      var first = adapter.fetchContent(account, query);
      assertEquals(ContentType.TEXT, first.content().getFirst().contentType());
      assertEquals("100", first.nextCursor());
      assertSame(adapter, context.getBean(LinkedInComponentFactory.class).adapter());
      var last =
          adapter.fetchContent(
              account,
              new ContentQuery(
                  1L,
                  null,
                  Fixtures.NOW,
                  100,
                  first.nextCursor(),
                  query.requestedTypes(),
                  query.mode()));
      assertFalse(last.hasMore());
      server.verify(
          getRequestedFor(urlPathEqualTo("/posts"))
              .withHeader("Authorization", equalTo("Bearer external-token"))
              .withHeader("Linkedin-Version", equalTo("202609"))
              .withHeader("X-Restli-Protocol-Version", equalTo("2.0.0"))
              .withHeader("X-RestLi-Method", equalTo("FINDER"))
              .withQueryParam("start", equalTo("100"))
              .withQueryParam("author", equalTo("urn:li:organization:42")));
    } finally {
      server.stop();
    }
  }
}
