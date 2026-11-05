package com.slokam.social.providers.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.providers.GraphApiClient;
import org.springframework.stereotype.Component;

@Component
public class InstagramApiClient extends GraphApiClient {
  public static final String FIELDS =
      "id,caption,media_type,media_product_type,media_url,permalink,thumbnail_url,timestamp,username";

  public InstagramApiClient(ProviderHttpClient h, CredentialResolver c, SocialProperties p) {
    super(h, c, p);
  }

  public JsonNode media(
      SocialAccount a,
      String edge,
      String cursor,
      int size,
      java.time.Instant from,
      java.time.Instant to) {
    boolean metrics =
        properties.getPlatforms().get(a.platform()).getAccounts().stream()
            .anyMatch(
                c -> c.getExternalId().equals(a.externalAccountId()) && c.isEngagementEnabled());
    var bounds = new java.util.HashMap<String, String>();
    if (!edge.equals("stories")) {
      if (from != null) bounds.put("since", String.valueOf(from.getEpochSecond()));
      if (to != null) bounds.put("until", String.valueOf(to.getEpochSecond() + 1));
    }
    return edge(
        a,
        edge,
        FIELDS + (!edge.equals("stories") && metrics ? ",like_count,comments_count" : ""),
        cursor,
        size,
        bounds);
  }

  public JsonNode children(SocialAccount a, String id) {
    return allChildren(a, id, "children", "id,media_type,media_url,thumbnail_url,timestamp");
  }
}
