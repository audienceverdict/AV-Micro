package com.slokam.social.publishing;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PublishingHttp {
  public record Response(int status, HttpHeaders headers, JsonNode body) {}
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final PublishingProperties properties;
  public PublishingHttp(ObjectMapper mapper, PublishingProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  public Response get(String url, String token) {
    return send("GET", url, token, Map.of(), HttpRequest.BodyPublishers.noBody(), false);
  }

  public Response form(String url, String token, Map<String, String> values, boolean mutating) {
    String body = values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(java.util.stream.Collectors.joining("&"));
    return send("POST", url, token, Map.of("Content-Type", "application/x-www-form-urlencoded"),
        HttpRequest.BodyPublishers.ofString(body), mutating);
  }

  public Response json(String url, String token, Map<String, Object> value, Map<String, String> headers) {
    try {
      var all = new HashMap<>(headers);
      all.put("Content-Type", "application/json");
      return send("POST", url, token, all, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(value)), true);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new PublishFailure("FAILED", "Unable to serialize publishing request");
    }
  }

  public Response upload(String url, String token, Path file, String mime, long size, long offset) {
    var body = HttpRequest.BodyPublishers.ofInputStream(() -> {
      try {
        var input = Files.newInputStream(file);
        try { input.skipNBytes(offset); return input; }
        catch (Exception e) { input.close(); throw e; }
      } catch (Exception e) { throw new IllegalStateException("Cannot read saved media"); }
    });
    var headers = new HashMap<String, String>();
    headers.put("Content-Type", mime);
    headers.put("Content-Range", "bytes " + offset + "-" + (size - 1) + "/" + size);
    return send("PUT", url, token, headers,
        HttpRequest.BodyPublishers.fromPublisher(body, size - offset), true);
  }

  public Response uploadStatus(String url, String token, long size) {
    return send("PUT", url, token, Map.of("Content-Range", "bytes */" + size),
        HttpRequest.BodyPublishers.noBody(), false);
  }

  private Response send(String method, String url, String token, Map<String, String> headers,
      HttpRequest.BodyPublisher body, boolean mutating) {
    try {
      var builder = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds())).method(method, body);
      if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
      headers.forEach(builder::header);
      var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      JsonNode json;
      try { json = response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body()); }
      catch (Exception e) {
        throw new PublishFailure(mutating ? "UNKNOWN" : "RETRY", "Provider returned an invalid response");
      }
      if (status >= 400 || (status >= 300 && status != 308)) {
        int code = json.path("error").path("code").asInt();
        String state = status == 401 || status == 403 || code == 190 || code == 10 || code == 200
            ? "ACTION_REQUIRED" : status >= 500 || status == 408
                ? (mutating ? "UNKNOWN" : "RETRY") : status == 429 ? "RETRY" : "FAILED";
        // A rejected POST is safe to retry only after an explicit 429 response.
        long delay = retryDelay(response.headers().firstValue("Retry-After").orElse(null));
        if (delay > 86400) state = "ACTION_REQUIRED";
        throw new PublishFailure(state, "Provider HTTP " + status + "; check permissions, quota and media requirements", Math.min(86400, delay));
      }
      if (json == null || json.has("error"))
        throw new PublishFailure(mutating ? "UNKNOWN" : "RETRY", "Provider returned an invalid response");
      return new Response(status, response.headers(), json);
    } catch (PublishFailure e) { throw e; }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PublishFailure(mutating ? "UNKNOWN" : "RETRY", "Provider request interrupted");
    } catch (Exception e) {
      throw new PublishFailure(mutating ? "UNKNOWN" : "RETRY", "Provider transport failure; remote result may require reconciliation");
    }
  }

  private long retryDelay(String value) {
    if (value == null) return 300;
    try { return Math.max(300, Long.parseLong(value)); }
    catch (NumberFormatException ignored) {
      try {
        return Math.max(300, Duration.between(java.time.Instant.now(),
            java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds() + 1);
      } catch (Exception invalid) { return 300; }
    }
  }

  public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
