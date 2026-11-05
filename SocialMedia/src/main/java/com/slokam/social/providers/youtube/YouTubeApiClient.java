package com.slokam.social.providers.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class YouTubeApiClient {
  private final ProviderHttpClient http;
  private final CredentialResolver credentials;
  private final SocialProperties properties;

  public YouTubeApiClient(ProviderHttpClient h, CredentialResolver c, SocialProperties p) {
    http = h;
    credentials = c;
    properties = p;
  }

  public JsonNode get(SocialAccount a, String operation, Map<String, String> query) {
    String token = credentials.optional(a.credentialReference());
    var config =
        properties.getPlatforms().get("youtube").getAccounts().stream()
            .filter(c -> c.getExternalId().equals(a.externalAccountId()))
            .findFirst()
            .orElseThrow();
    String key = credentials.optional(config.getApiKeyReference());
    if (token == null && key == null)
      throw new com.slokam.social.core.exception.ProviderException(
          "YouTube requires an external API key or access token", false, null);
    return http.get("youtube", operation, query, token, key);
  }

  public JsonNode channel(SocialAccount a) {
    return get(
        a,
        "channels",
        Map.of("part", "snippet,contentDetails,statistics", "id", a.externalAccountId()));
  }

  public JsonNode playlists(SocialAccount a, String cursor, int size) {
    var q =
        new HashMap<>(
            Map.of(
                "part",
                "snippet,contentDetails",
                "channelId",
                a.externalAccountId(),
                "maxResults",
                String.valueOf(Math.min(50, size))));
    if (cursor != null) q.put("pageToken", cursor);
    return get(a, "playlists", q);
  }

  public JsonNode playlistItems(SocialAccount a, String playlist, String cursor, int size) {
    var q =
        new HashMap<>(
            Map.of(
                "part",
                "snippet,contentDetails",
                "playlistId",
                playlist,
                "maxResults",
                String.valueOf(Math.min(50, size))));
    if (cursor != null) q.put("pageToken", cursor);
    return get(a, "playlistItems", q);
  }

  public JsonNode videos(SocialAccount a, String ids) {
    return get(
        a,
        "videos",
        Map.of("part", "snippet,contentDetails,statistics,liveStreamingDetails,status", "id", ids));
  }
}
