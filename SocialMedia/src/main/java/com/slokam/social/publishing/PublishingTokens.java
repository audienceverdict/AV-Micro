package com.slokam.social.publishing;

import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PublishingTokens {
  private record Cached(String value, long expiresAt) {}
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();
  private final PublishingProperties properties;
  private final CredentialResolver credentials;
  private final PublishingHttp http;
  public PublishingTokens(PublishingProperties properties, CredentialResolver credentials, PublishingHttp http) {
    this.properties = properties;
    this.credentials = credentials;
    this.http = http;
  }

  public synchronized String token(String platform, SocialProperties.Account account) {
    if (platform.equals("youtube")) {
      String refresh = credentials.optional(account.getRefreshTokenReference());
      if (refresh != null) {
        String key = platform + ":" + account.getExternalId();
        var cached = cache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();
        var youtube = properties.getYoutube();
        if (youtube.getClientId().isBlank() || youtube.getClientSecret().isBlank())
          throw new PublishFailure("ACTION_REQUIRED", "Configure YouTube client-id and client-secret");
        com.fasterxml.jackson.databind.JsonNode body;
        try {
          body = http.form(youtube.getTokenUrl(), null, Map.of(
            "grant_type", "refresh_token", "client_id", youtube.getClientId(),
            "client_secret", youtube.getClientSecret(), "refresh_token", refresh), false).body();
        } catch (PublishFailure failure) {
          if (failure.status().equals("FAILED"))
            throw new PublishFailure("ACTION_REQUIRED", "YouTube OAuth credentials were rejected; check client settings and reconnect account");
          throw failure;
        }
        String value = body.path("access_token").asText();
        if (value.isBlank()) throw new PublishFailure("ACTION_REQUIRED", "YouTube refresh token was rejected; reconnect account");
        cache.put(key, new Cached(value, System.currentTimeMillis() + Math.max(0, body.path("expires_in").asLong(3600) - 60) * 1000));
        return value;
      }
    }
    String value = credentials.optional(account.getCredentialReference());
    if (value == null) throw new PublishFailure("ACTION_REQUIRED", "Configure an account access token with publishing permissions");
    return value;
  }

  public void invalidate(String platform, String accountId) { cache.remove(platform + ":" + accountId); }
}
