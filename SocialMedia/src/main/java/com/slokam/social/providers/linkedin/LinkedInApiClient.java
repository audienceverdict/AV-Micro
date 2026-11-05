package com.slokam.social.providers.linkedin;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.*;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "social.platforms.linkedin.enabled",
    havingValue = "true")
public class LinkedInApiClient {
  private final ProviderHttpClient http;
  private final CredentialResolver credentials;
  private final SocialProperties properties;

  public LinkedInApiClient(ProviderHttpClient h, CredentialResolver c, SocialProperties p) {
    http = h;
    credentials = c;
    properties = p;
  }

  public JsonNode posts(SocialAccount a, String cursor, int size) {
    int start = cursor == null ? 0 : Integer.parseInt(cursor);
    if (start < 0) throw new IllegalArgumentException("Invalid LinkedIn start");
    String version = properties.getPlatforms().get("linkedin").getApiVersion();
    if (!version.matches("[0-9]{6}"))
      throw new IllegalStateException("Configure a supported LinkedIn YYYYMM API version");
    return http.get(
        "linkedin",
        "posts",
        Map.of(
            "q",
            "author",
            "author",
            a.externalAccountId(),
            "sortBy",
            "LAST_MODIFIED",
            "count",
            String.valueOf(Math.min(100, size)),
            "start",
            String.valueOf(start)),
        credentials.required(a.credentialReference()),
        null,
        Map.of(
            "Linkedin-Version",
            version,
            "X-Restli-Protocol-Version",
            "2.0.0",
            "X-RestLi-Method",
            "FINDER"));
  }

  public String next(JsonNode response) {
    var links = response.path("paging").path("links");
    if (!links.isArray()) throw new IllegalArgumentException("Missing LinkedIn paging links");
    for (var link : links)
      if ("next".equals(link.path("rel").asText())) {
        String raw = java.net.URI.create(link.path("href").asText()).getRawQuery();
        if (raw != null)
          for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("start")) {
              int start = Integer.parseInt(pair[1]);
              if (start < 0) break;
              return String.valueOf(start);
            }
          }
        throw new IllegalArgumentException("Missing LinkedIn next start");
      }
    return null;
  }
}
