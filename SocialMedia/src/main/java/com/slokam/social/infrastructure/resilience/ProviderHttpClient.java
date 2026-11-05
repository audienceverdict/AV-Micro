package com.slokam.social.infrastructure.resilience;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.exception.ProviderException;
import io.github.resilience4j.ratelimiter.*;
import io.github.resilience4j.retry.*;
import io.netty.channel.ChannelOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.netty.http.client.HttpClient;

@Component
@lombok.extern.slf4j.Slf4j
public class ProviderHttpClient {
  private final SocialProperties properties;
  private final WebClient client;
  private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

  public ProviderHttpClient(SocialProperties properties) {
    this.properties = properties;
    var cfg = properties.getHttp();
    client =
        WebClient.builder()
            .clientConnector(
                new ReactorClientHttpConnector(
                    HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, cfg.getConnectTimeoutMs())
                        .responseTimeout(Duration.ofSeconds(cfg.getReadTimeoutSeconds()))))
            .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
            .build();
  }

  public JsonNode get(
      String platform, String path, Map<String, String> params, String bearer, String apiKey) {
    return get(platform, path, params, bearer, apiKey, Map.of());
  }

  public JsonNode get(
      String platform,
      String path,
      Map<String, String> params,
      String bearer,
      String apiKey,
      Map<String, String> extraHeaders) {
    return request(platform, null, path, params, bearer, apiKey, extraHeaders);
  }

  public JsonNode youtubeAnalytics(Map<String, String> params, String bearer) {
    return request("youtube", "https://youtubeanalytics.googleapis.com/v2", "reports",
        params, bearer, null, Map.of());
  }

  private JsonNode request(String platform, String endpoint, String path,
      Map<String, String> params, String bearer, String apiKey, Map<String, String> extraHeaders) {
    var p = properties.getPlatforms().get(platform);
    if (p == null || p.getBaseUrl() == null)
      throw new IllegalStateException("Missing provider configuration");
    // Only configured origins and relative API paths are allowed. Never follow provider next URLs
    // containing tokens.
    if (!path.matches("[A-Za-z0-9_./-]+") || path.contains(".."))
      throw new IllegalArgumentException("Invalid provider path");
    var h = properties.getHttp();
    var retry =
        Retry.of(
            "provider",
            RetryConfig.custom()
                .maxAttempts(h.getMaxAttempts())
                .retryOnException(e -> e instanceof ProviderException pe && pe.retryable())
                .intervalBiFunction(
                    (attempt, result) -> {
                      long backoff =
                          Math.min(30000L, h.getRetryBaseMs() * (1L << Math.min(attempt - 1, 16)));
                      long delay = ThreadLocalRandom.current().nextLong(backoff / 2, backoff + 1);
                      if (result.isLeft()
                          && result.getLeft() instanceof ProviderException pe
                          && pe.retryAfter() != null)
                        delay = Math.max(delay, pe.retryAfter().toMillis());
                      return delay;
                    })
                .build());
    retry
        .getEventPublisher()
        .onRetry(
            event ->
                log.warn(
                    "Retrying provider request platform={} attempt={} delayMs={}",
                    platform,
                    event.getNumberOfRetryAttempts(),
                    event.getWaitInterval().toMillis()));
    return Retry.decorateSupplier(
            retry,
            () -> {
              var limiter =
                  limiters.computeIfAbsent(
                      platform,
                      key ->
                          RateLimiter.of(
                              key,
                              RateLimiterConfig.custom()
                                  .limitRefreshPeriod(Duration.ofMinutes(1))
                                  .limitForPeriod(h.getRequestsPerMinute())
                                  .timeoutDuration(Duration.ofSeconds(h.getReadTimeoutSeconds()))
                                  .build()));
              if (!limiter.acquirePermission()) {
                log.warn("Local provider rate limit reached platform={}", platform);
                throw new ProviderException("Local provider rate limit", true, null);
              }
              log.debug("Sending provider request platform={}", platform);
              try {
                return client
                    .get()
                    .uri(uri((endpoint == null ? p.getBaseUrl() : endpoint) + "/" + path, params, apiKey))
                    .headers(
                        headers -> {
                          extraHeaders.forEach(headers::set);
                          if (bearer != null) headers.setBearerAuth(bearer);
                        })
                    .exchangeToMono(
                        response -> {
                          if (response.statusCode().isError()) {
                            int status = response.statusCode().value();
                            Duration wait =
                                parseRetryAfter(
                                    response.headers().asHttpHeaders().getFirst("Retry-After"));
                            return response
                                .bodyToMono(JsonNode.class)
                                .defaultIfEmpty(
                                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                        .objectNode())
                                .onErrorReturn(
                                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                        .objectNode())
                                .flatMap(
                                    body -> {
                                      int code = body.path("error").path("code").asInt();
                                      String reason =
                                          body.path("error")
                                              .path("errors")
                                              .path(0)
                                              .path("reason")
                                              .asText("");
                                      String category =
                                          code == 190
                                              ? " (invalid or expired credential)"
                                              : code == 10 || code == 200
                                                  ? " (missing provider permission)"
                                                  : "";
                                      if (reason.equals("quotaExceeded")
                                          || reason.equals("dailyLimitExceeded"))
                                        category = " (provider quota exhausted)";
                                      boolean throttled =
                                          Set.of(4, 17, 32, 613, 80001).contains(code)
                                              || reason.equals("rateLimitExceeded")
                                              || reason.equals("userRateLimitExceeded");
                                      boolean authorization =
                                          status == 401 || code == 190 || code == 10 || code == 200;
                                      boolean retryable =
                                          !authorization
                                              && (status == 429 || status >= 500 || throttled);
                                      if (wait != null
                                          && wait.getSeconds() > h.getMaxRetryAfterSeconds())
                                        retryable = false;
                                      log.warn(
                                          "Provider request rejected platform={} httpStatus={} providerCode={} category={} retryable={} retryAfter={}",
                                          platform,
                                          status,
                                          code,
                                          category,
                                          retryable,
                                          wait);
                                      return reactor.core.publisher.Mono.error(
                                          new ProviderException(
                                              "Provider HTTP "
                                                  + status
                                                  + category
                                                  + (throttled
                                                      ? " (rate limit)"
                                                      : category.isEmpty()
                                                              && (status == 401 || status == 403)
                                                          ? " (credential or permission failure)"
                                                          : ""),
                                              retryable,
                                              wait,
                                              reason.equals("quotaExceeded") || reason.equals("dailyLimitExceeded")
                                                  ? "QUOTA_EXCEEDED"
                                                  : status == 401 || code == 190 ? "AUTH_REQUIRED"
                                                  : status == 403 || code == 10 || code == 200 ? "PERMISSION_DENIED"
                                                  : status == 404 ? "NOT_FOUND"
                                                  : status == 400 ? "REJECTED" : "FAILED"));
                                    });
                          }
                          return response.bodyToMono(JsonNode.class);
                        })
                    .block(Duration.ofSeconds(h.getReadTimeoutSeconds() + 5L));
              } catch (ProviderException e) {
                throw e;
              } catch (Exception e) {
                // WebClient exceptions may embed a URL/API key. Do not propagate their text or
                // cause.
                log.warn(
                    "Provider transport/response failure platform={} exceptionType={}",
                    platform,
                    e.getClass().getSimpleName());
                boolean network =
                    e instanceof WebClientRequestException || e instanceof IllegalStateException;
                throw new ProviderException(
                    network ? "Provider transport timeout/failure" : "Invalid provider response",
                    network,
                    null);
              }
            })
        .get();
  }

  private java.net.URI uri(String url, Map<String, String> params, String apiKey) {
    var builder = org.springframework.web.util.UriComponentsBuilder.fromUriString(url);
    var values = new HashMap<String, String>();
    var all = new LinkedHashMap<>(params);
    if (apiKey != null) all.put("key", apiKey);
    int index = 0;
    for (var entry : all.entrySet()) {
      String variable = "value" + index++;
      builder.queryParam(entry.getKey(), "{" + variable + "}");
      values.put(variable, entry.getValue());
    }
    return builder.encode().buildAndExpand(values).toUri();
  }

  static Duration parseRetryAfter(String value) {
    if (value == null) return null;
    try {
      return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
    } catch (NumberFormatException e) {
      try {
        return Duration.between(
                    Instant.now(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                .isNegative()
            ? Duration.ZERO
            : Duration.between(
                Instant.now(),
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
      } catch (Exception ignored) {
        return null;
      }
    }
  }
}
