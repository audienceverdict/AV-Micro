package com.slokam.platform;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.location=file:../config-repo/api-gateway.yml", "spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
class SocialMediaRouteTest {
  static final DisposableServer backend = HttpServer.create().port(0)
      .handle((request, response) -> response.sendString(Mono.just(request.uri() + "|"
          + request.requestHeaders().get("Authorization", "") + "|"
          + request.requestHeaders().get("X-Service-Token", ""))))
      .bindNow();
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    for (String service : java.util.List.of("identity-service", "av-service", "social-media-service")) {
      registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
          () -> "http://localhost:" + backend.port());
    }
  }
  @Autowired org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext context;
  @AfterAll static void shutdown() { backend.disposeNow(); }
  @Test void routesSocialApiBeforeAvAndPreservesBearerWhileRemovingInternalHeaders() {
    var client = WebTestClient.bindToServer().baseUrl("http://localhost:" + context.getWebServer().getPort()).build();
    client.get().uri("/api/v1/social/accounts?after=3")
        .header("Authorization", "Bearer identity-token").header("X-Service-Token", "spoofed")
        .exchange().expectStatus().isOk().expectBody(String.class)
        .isEqualTo("/api/accounts?after=3|Bearer identity-token|");
    client.get().uri("/social/v3/api-docs").exchange().expectStatus().isOk()
        .expectBody(String.class).isEqualTo("/v3/api-docs||");
  }
  @Test void routesUploadCommentsAndAllowsIdempotencyHeaderFromFrontend() {
    var client = WebTestClient.bindToServer().baseUrl("http://localhost:" + context.getWebServer().getPort()).build();
    client.post().uri("/api/v1/social/uploads/42/comments")
        .header("Authorization", "Bearer admin-token").header("Idempotency-Key","comment-42")
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue("{\"text\":\"Hello\"}")
        .exchange().expectStatus().isOk().expectBody(String.class)
        .isEqualTo("/api/uploads/42/comments|Bearer admin-token|");
    client.options().uri("/api/v1/social/uploads/42/comments")
        .header("Origin","http://localhost:5173")
        .header("Access-Control-Request-Method","POST")
        .header("Access-Control-Request-Headers","authorization,content-type,idempotency-key")
        .exchange().expectStatus().isOk()
        .expectHeader().valueMatches("Access-Control-Allow-Headers","(?i).*idempotency-key.*");
  }
  @Test void routesIdentityAvAndDocsFromCentralConfiguration() {
    var client = WebTestClient.bindToServer().baseUrl("http://localhost:" + context.getWebServer().getPort()).build();
    for (String path : java.util.List.of("/api/v1/auth/otp/config", "/api/v1/admin/users",
        "/api/v1/admin/users/123/role", "/.well-known/jwks.json", "/api/v1/movies")) {
      client.get().uri(path).exchange().expectStatus().isOk()
          .expectBody(String.class).isEqualTo(path + "||");
    }
    for (String service : java.util.List.of("identity", "av")) {
      client.get().uri("/" + service + "/v3/api-docs").exchange().expectStatus().isOk()
          .expectBody(String.class).isEqualTo("/v3/api-docs||");
    }
    client.get().uri("/internal/users").exchange().expectStatus().isNotFound();
    client.get().uri("/internal/shows/123/exists").exchange().expectStatus().isNotFound();
  }

  @Autowired org.springframework.cloud.gateway.route.RouteLocator routeLocator;
  @Test void centralRoutesResolveToTheCorrectServicesWithExplicitPriority() {
    var routes = routeLocator.getRoutes().collectMap(org.springframework.cloud.gateway.route.Route::getId).block();
    org.assertj.core.api.Assertions.assertThat(routes).hasSize(7);
    for (String id : java.util.List.of("identity", "jwks", "identity-docs")) {
      org.assertj.core.api.Assertions.assertThat(routes.get(id).getUri().toString()).isEqualTo("lb://identity-service");
    }
    for (String id : java.util.List.of("social-media", "social-media-docs")) {
      org.assertj.core.api.Assertions.assertThat(routes.get(id).getUri().toString()).isEqualTo("lb://social-media-service");
    }
    for (String id : java.util.List.of("av", "av-docs")) {
      org.assertj.core.api.Assertions.assertThat(routes.get(id).getUri().toString()).isEqualTo("lb://av-service");
    }
    org.assertj.core.api.Assertions.assertThat(routes.get("identity").getOrder()).isLessThan(routes.get("av").getOrder());
    org.assertj.core.api.Assertions.assertThat(routes.get("social-media").getOrder()).isLessThan(routes.get("av").getOrder());
  }

}
