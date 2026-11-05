package com.slokam.social.providers.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.providers.GraphApiClient;
import org.springframework.stereotype.Component;

@Component
public class FacebookApiClient extends GraphApiClient {
  public FacebookApiClient(ProviderHttpClient h, CredentialResolver c, SocialProperties p) {
    super(h, c, p);
  }

  public JsonNode feed(SocialAccount a, String cursor, int size) {
    boolean metrics =
        properties.getPlatforms().get(a.platform()).getAccounts().stream()
            .anyMatch(
                c -> c.getExternalId().equals(a.externalAccountId()) && c.isEngagementEnabled());
    return edge(
        a,
        "feed",
        "id,message,created_time,updated_time,permalink_url,from"
            + (metrics
                ? ",reactions.limit(0).summary(true),comments.limit(0).summary(true),shares"
                : ""),
        cursor,
        size);
  }

  public boolean includePhotos(SocialAccount a) {
    return properties.getPlatforms().get(a.platform()).getAccounts().stream()
        .anyMatch(c -> c.getExternalId().equals(a.externalAccountId()) && c.isIncludePhotos());
  }

  public JsonNode photos(SocialAccount a, String cursor, int size) {
    return edge(
        a,
        "photos",
        "id,name,created_time,updated_time,link,picture,images,from",
        cursor,
        size,
        java.util.Map.of("type", "uploaded"));
  }

  public JsonNode video(SocialAccount a, String id) {
    return get(
        a,
        id,
        java.util.Map.of("fields", "id,source,picture,created_time,updated_time,description,from"));
  }

  public JsonNode attachments(SocialAccount a, String id) {
    return allChildren(
        a,
        id,
        "attachments",
        "media,type,url,target,subattachments.limit(100){media,type,url,target}");
  }
}
