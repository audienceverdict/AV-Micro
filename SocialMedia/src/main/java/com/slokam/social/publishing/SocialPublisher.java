package com.slokam.social.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SocialPublisher {
  public record Result(String status, String reference, String externalId, String permalink) {
    static Result processing(String reference) { return new Result("PROCESSING", reference, null, null); }
    static Result checking(String id, String link) { return new Result("CHECKING", null, id, link); }
    static Result published(String id, String link) { return new Result("PUBLISHED", null, id, link); }
  }
  private final SocialProperties social;
  private final PublishingProperties properties;
  private final PublishingTokens tokens;
  private final PublishingMediaStore media;
  private final PublishingHttp http;
  public SocialPublisher(SocialProperties social, PublishingProperties properties, PublishingTokens tokens,
      PublishingMediaStore media, PublishingHttp http) {
    this.social = social;
    this.properties = properties;
    this.tokens = tokens;
    this.media = media;
    this.http = http;
  }

  public Result advance(Publication job) {
    var platform = social.getPlatforms().get(job.platform());
    if (platform == null || !platform.isEnabled())
      throw new PublishFailure("ACTION_REQUIRED", "Platform is disabled or missing");
    var account = platform.getAccounts().stream()
        .filter(a -> a.getExternalId().equals(job.accountId()) && a.isEnabled() && a.isPublishingEnabled())
        .findFirst().orElseThrow(() -> new PublishFailure("ACTION_REQUIRED", "Account is disabled or missing"));
    validate(job);
    String token = tokens.token(job.platform(), account);
    return switch (job.platform()) {
      case "youtube" -> youtube(job, token, platform);
      case "instagram" -> instagram(job, token, platform);
      case "facebook" -> facebook(job, token, platform);
      default -> throw new PublishFailure("FAILED", "Unsupported publishing platform");
    };
  }

  private void validate(Publication job) {
    if (job.platform().equals("youtube") && !job.contentType().equals("VIDEO"))
      throw new PublishFailure("FAILED", "YouTube publishing requires video content");
    if (job.platform().equals("instagram")) {
      if (job.contentType().equals("TEXT")) throw new PublishFailure("FAILED", "Instagram requires an image or video");
      if (job.caption().length() > 2200) throw new PublishFailure("FAILED", "Instagram captions must be at most 2200 characters");
      if (job.contentType().equals("IMAGE") && !"image/jpeg".equals(job.mediaType()))
        throw new PublishFailure("FAILED", "Instagram images must be JPEG");
    }
    if (job.platform().equals("youtube") && job.caption().length() > 5000)
      throw new PublishFailure("FAILED", "YouTube descriptions must be at most 5000 characters");
  }

  private Result instagram(Publication job, String token, SocialProperties.Platform platform) {
    String base = graph(platform);
    if (job.stage().equals("CHECKING")) {
      var body = graphGet(base + "/" + job.externalId() + "?fields=permalink", token).body();
      return Result.published(job.externalId(), body.path("permalink").asText(""));
    }
    if (job.stage().equals("QUEUED")) {
      var form = new LinkedHashMap<String, String>();
      form.put("caption", job.caption());
      if (job.contentType().equals("VIDEO")) {
        form.put("media_type", "REELS");
        form.put("video_url", media.publicUrl(job.contentId()));
      } else form.put("image_url", media.publicUrl(job.contentId()));
      return Result.processing(id(graphForm(base + "/" + job.accountId() + "/media", token, form, true).body()));
    }
    String container = job.remoteReference();
    var state = graphGet(base + "/" + container + "?fields=status_code", token).body().path("status_code").asText();
    if (Set.of("ERROR", "EXPIRED").contains(state)) throw new PublishFailure("FAILED", "Instagram media processing failed or container expired; create new content after fixing media");
    if (!state.equals("FINISHED")) return Result.processing(container);
    String published = id(graphForm(base + "/" + job.accountId() + "/media_publish", token,
        Map.of("creation_id", container), true).body());
    // Record the known published ID before looking up its permalink.
    return Result.checking(published, null);
  }

  private Result facebook(Publication job, String token, SocialProperties.Platform platform) {
    String base = graph(platform);
    if (job.stage().equals("CHECKING")) {
      var body = graphGet(base + "/" + job.externalId() + "?fields=status", token).body();
      String state = body.path("status").path("video_status").asText();
      if (state.equals("error")) throw new PublishFailure("FAILED", "Facebook video processing failed");
      if (state.equals("ready")) return Result.published(job.externalId(), "https://www.facebook.com/" + job.externalId());
      return Result.checking(job.externalId(), null);
    }
    var form = new LinkedHashMap<String, String>();
    String edge;
    switch (job.contentType()) {
      case "TEXT" -> { edge = "feed"; form.put("message", job.caption()); }
      case "IMAGE" -> {
        edge = "photos";
        form.put("url", media.publicUrl(job.contentId()));
        form.put("caption", job.caption());
      }
      case "VIDEO" -> {
        edge = "videos";
        form.put("file_url", media.publicUrl(job.contentId()));
        form.put("title", job.title());
        form.put("description", job.caption());
      }
      default -> throw new PublishFailure("FAILED", "Unsupported Facebook content type");
    }
    var body = graphForm(base + "/" + job.accountId() + "/" + edge, token, form, true).body();
    String published = body.path("post_id").asText();
    if (published.isBlank()) published = id(body);
    return job.contentType().equals("VIDEO") ? Result.checking(published, null)
        : Result.published(published, "https://www.facebook.com/" + published);
  }

  private Result youtube(Publication job, String token, SocialProperties.Platform platform) {
    String base = platform.getBaseUrl().replaceAll("/+$", "");
    if (job.stage().equals("CHECKING")) {
      var item = http.get(base + "/videos?part=status&id=" + job.externalId(), token).body().path("items").path(0);
      if (item.isMissingNode()) throw new PublishFailure("ACTION_REQUIRED", "YouTube upload is not visible with the configured account token");
      String state = item.path("status").path("uploadStatus").asText();
      if (Set.of("failed", "rejected", "deleted").contains(state))
        throw new PublishFailure("FAILED", "YouTube rejected or could not process the video");
      if (state.equals("processed")) return Result.published(job.externalId(), "https://www.youtube.com/watch?v=" + job.externalId());
      return Result.checking(job.externalId(), null);
    }
    if (job.stage().equals("QUEUED")) {
      // Tokens belong to channels: prevent accidentally uploading to the wrong configured channel.
      var channels = http.get(base + "/channels?part=id&mine=true", token).body().path("items");
      boolean owner = false;
      for (var channel : channels) if (channel.path("id").asText().equals(job.accountId())) owner = true;
      if (!owner) throw new PublishFailure("ACTION_REQUIRED", "YouTube token does not own the configured channel; authorize with upload and readonly scopes");
      var response = http.json(properties.getYoutube().getUploadUrl() + "?uploadType=resumable&part=snippet,status", token,
          Map.of("snippet", Map.of("title", job.title(), "description", job.caption()),
              "status", Map.of("privacyStatus", job.privacy())),
          Map.of("X-Upload-Content-Length", Long.toString(job.mediaSize()), "X-Upload-Content-Type", job.mediaType()));
      String location = response.headers().firstValue("Location")
          .orElseThrow(() -> new PublishFailure("UNKNOWN", "YouTube did not return an upload session"));
      validateSession(location);
      return Result.processing(location);
    }
    validateSession(job.remoteReference());
    var response = http.uploadStatus(job.remoteReference(), token, job.mediaSize());
    if (response.status() != 308) return Result.checking(id(response.body()), null);
    long offset = response.headers().firstValue("Range").map(range -> {
      if (!range.matches("bytes=0-[0-9]+")) throw new PublishFailure("ACTION_REQUIRED", "Invalid YouTube upload range");
      return Long.parseLong(range.substring(range.indexOf('-') + 1)) + 1;
    }).orElse(0L);
    if (offset >= job.mediaSize()) throw new PublishFailure("RETRY", "Waiting for YouTube upload confirmation");
    response = http.upload(job.remoteReference(), token, media.path(job.contentId()), job.mediaType(), job.mediaSize(), offset);
    if (response.status() == 308) return Result.processing(job.remoteReference());
    return Result.checking(id(response.body()), null);
  }

  private void validateSession(String location) {
    try {
      var expected = URI.create(properties.getYoutube().getUploadUrl());
      var actual = URI.create(location);
      if (!Objects.equals(expected.getScheme(), actual.getScheme())
          || !Objects.equals(expected.getHost(), actual.getHost()) || expected.getPort() != actual.getPort()
          || !Objects.equals(expected.getPath(), actual.getPath()) || actual.getUserInfo() != null
          || actual.getFragment() != null) throw new IllegalArgumentException();
    } catch (Exception e) { throw new PublishFailure("ACTION_REQUIRED", "YouTube returned an untrusted upload session URL"); }
  }

  private PublishingHttp.Response graphForm(String url, String token, Map<String, String> values, boolean mutating) {
    var form = new LinkedHashMap<>(values);
    String proof = appSecretProof(token);
    if (proof != null) form.put("appsecret_proof", proof);
    return http.form(url, token, form, mutating);
  }

  private PublishingHttp.Response graphGet(String url, String token) {
    String proof = appSecretProof(token);
    return http.get(proof == null ? url : url + (url.contains("?") ? "&" : "?") + "appsecret_proof=" + proof, token);
  }

  private String appSecretProof(String token) {
    String secret = properties.getMeta().getClientSecret();
    if (secret == null || secret.isBlank()) return null;
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new PublishFailure("ACTION_REQUIRED", "Unable to generate Meta app secret proof");
    }
  }

  private String graph(SocialProperties.Platform platform) {
    return platform.getBaseUrl().replaceAll("/+$", "") + "/" + platform.getApiVersion();
  }

  private String id(JsonNode body) {
    String id = body.path("id").asText();
    if (!id.matches("[A-Za-z0-9_-]{1,191}")) throw new PublishFailure("UNKNOWN", "Provider did not return a valid content ID");
    return id;
  }

}
