package com.slokam.social.providers.instagram;

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
public class InstagramAdapter extends AbstractGraphAdapter {
  private final InstagramApiClient client;
  private final ContentMapper mapper;

  public InstagramAdapter(InstagramApiClient c, @Qualifier("instagramMapper") ContentMapper m) {
    client = c;
    mapper = m;
  }

  public String platformId() {
    return "instagram";
  }

  public PlatformCapabilities capabilities() {
    return new PlatformCapabilities(
        Set.of(
            ContentType.IMAGE,
            ContentType.VIDEO,
            ContentType.REEL,
            ContentType.CAROUSEL,
            ContentType.STORY),
        true,
        false,
        true);
  }

  private boolean stories(ContentQuery q) {
    return q.requestedTypes().equals(Set.of(ContentType.STORY))
        || (q.cursor() != null && q.cursor().startsWith("stories:"));
  }

  protected JsonNode profile(SocialAccount a) {
    return client.profile(a, "id,username,name,biography,profile_picture_url");
  }

  protected JsonNode page(SocialAccount a, ContentQuery q) {
    String cursor = q.cursor() == null ? null : q.cursor().substring(q.cursor().indexOf(':') + 1);
    if (cursor != null && cursor.isBlank()) cursor = null;
    return client.media(
        a, stories(q) ? "stories" : "media", cursor, q.pageSize(), q.from(), q.to());
  }

  protected ObjectNode enrich(SocialAccount a, ContentQuery q, JsonNode raw, JsonNode profile) {
    ObjectNode n = raw.deepCopy();
    if ("CAROUSEL_ALBUM".equals(text(n, "media_type")))
      n.set("children", client.children(a, text(n, "id")));
    n.set("profile", profile);
    if (stories(q)) n.put("_story", true);
    return n;
  }

  protected String next(SocialAccount a, ContentQuery q, JsonNode page) {
    String next = client.next(page);
    if (next != null) return (stories(q) ? "stories:" : "media:") + next;
    return null;
  }

  protected ContentMapper mapper() {
    return mapper;
  }
}
