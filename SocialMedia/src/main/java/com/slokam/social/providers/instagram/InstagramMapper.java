package com.slokam.social.providers.instagram;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.ContentClassifier;
import com.slokam.social.core.spi.ContentMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InstagramMapper implements ContentMapper {
  private final ContentClassifier classifier;

  public InstagramMapper(@Qualifier("instagramClassifier") ContentClassifier c) {
    classifier = c;
  }

  public SocialContent map(SocialAccount a, JsonNode n) {
    var type = classifier.classify(n);
    var assets = new ArrayList<MediaAsset>();
    if (type == ContentType.CAROUSEL) {
      for (var child : array(n, "children")) assets.add(asset(child, assets.size()));
    } else assets.add(asset(n, 0));
    var metrics = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    if (n.has("like_count")) metrics.set("like_count", n.get("like_count"));
    if (n.has("comments_count")) metrics.set("comments_count", n.get("comments_count"));
    return content(
        a,
        n,
        type,
        text(n, "media_type"),
        a.externalAccountId(),
        text(n, "username"),
        null,
        text(n, "caption"),
        text(n, "permalink"),
        text(n, "thumbnail_url"),
        instant(text(n, "timestamp")),
        null,
        metrics.isEmpty() ? null : metrics,
        assets);
  }

  private MediaAsset asset(JsonNode n, int order) {
    return new MediaAsset(
        null,
        null,
        text(n, "id"),
        text(n, "media_type"),
        text(n, "media_url"),
        text(n, "thumbnail_url"),
        null,
        null,
        null,
        order);
  }
}
