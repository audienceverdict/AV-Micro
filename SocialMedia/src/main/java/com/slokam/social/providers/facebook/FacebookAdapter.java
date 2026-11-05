package com.slokam.social.providers.facebook;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slokam.social.core.capability.PlatformCapabilities;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.*;
import com.slokam.social.providers.AbstractGraphAdapter;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class FacebookAdapter extends AbstractGraphAdapter {
  private final FacebookApiClient client;
  private final ContentMapper mapper;

  public FacebookAdapter(FacebookApiClient c, @Qualifier("facebookMapper") ContentMapper m) {
    client = c;
    mapper = m;
  }

  public String platformId() {
    return "facebook";
  }

  public PlatformCapabilities capabilities() {
    return new PlatformCapabilities(
        Set.of(
            ContentType.TEXT,
            ContentType.IMAGE,
            ContentType.VIDEO,
            ContentType.CAROUSEL,
            ContentType.REEL,
            ContentType.LIVE,
            ContentType.UNKNOWN),
        false,
        true,
        false);
  }

  protected JsonNode profile(SocialAccount a) {
    return client.profile(a, "id,name,link");
  }

  protected JsonNode page(SocialAccount a, ContentQuery q) {
    String cursor = q.cursor();
    if (cursor != null && cursor.startsWith("photos:"))
      return client.photos(
          a, cursor.substring(7).isBlank() ? null : cursor.substring(7), q.pageSize());
    if (cursor != null && cursor.startsWith("feed:")) cursor = cursor.substring(5);
    return client.feed(a, cursor, q.pageSize());
  }

  protected ObjectNode enrich(SocialAccount a, ContentQuery q, JsonNode raw, JsonNode profile) {
    ObjectNode n = raw.deepCopy();
    if (q.cursor() != null && q.cursor().startsWith("photos:")) {
      n.put("_photo", true);
      n.set("profile", profile);
      return n;
    }
    n.set("attachments", client.attachments(a, text(n, "id")));
    n.set("profile", profile);
    var metrics = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    for (String field : List.of("reactions", "comments", "shares"))
      if (n.has(field)) metrics.set(field, n.get(field));
    if (!metrics.isEmpty()) n.set("engagement", metrics);
    return n;
  }

  protected String next(SocialAccount a, ContentQuery q, JsonNode page) {
    String next = client.next(page);
    boolean photos = q.cursor() != null && q.cursor().startsWith("photos:");
    if (next != null) return (photos ? "photos:" : "feed:") + next;
    return !photos && client.includePhotos(a) ? "photos:" : null;
  }

  protected ContentMapper mapper() {
    return mapper;
  }
}
