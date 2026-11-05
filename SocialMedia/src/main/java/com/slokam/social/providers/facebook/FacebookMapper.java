package com.slokam.social.providers.facebook;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.ContentClassifier;
import com.slokam.social.core.spi.ContentMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class FacebookMapper implements ContentMapper {
  private final ContentClassifier classifier;

  public FacebookMapper(@Qualifier("facebookClassifier") ContentClassifier c) {
    classifier = c;
  }

  public SocialContent map(SocialAccount a, JsonNode n) {
    if (n.path("_photo").asBoolean()) return photo(a, n);
    var assets = new ArrayList<MediaAsset>();
    for (var attachment : array(n, "attachments")) {
      if (attachment.path("subattachments").path("paging").hasNonNull("next"))
        throw new IllegalArgumentException("Incomplete Facebook subattachments");
      if (attachment.path("subattachments").has("data")) {
        for (var child : array(attachment.path("subattachments"), "data"))
          assets.add(asset(child, assets.size()));
      } else assets.add(asset(attachment, assets.size()));
    }
    var first = n.path("attachments").path(0);
    return content(
        a,
        n,
        classifier.classify(n),
        text(first, "type"),
        text(n.path("from"), "id"),
        text(n.path("from"), "name"),
        null,
        text(n, "message"),
        text(n, "permalink_url"),
        text(first.path("media").path("image"), "src"),
        instant(text(n, "created_time")),
        instant(text(n, "updated_time")),
        n.get("engagement"),
        assets);
  }

  private SocialContent photo(SocialAccount a, JsonNode n) {
    var image = n.path("images").path(0);
    String source = text(image, "source");
    String thumb = text(n, "picture");
    String id = text(n, "id");
    var asset =
        new MediaAsset(
            null,
            null,
            id,
            "IMAGE",
            source,
            thumb,
            integer(image, "width"),
            integer(image, "height"),
            null,
            0);
    return content(
        a,
        n,
        ContentType.IMAGE,
        "photo",
        text(n.path("from"), "id"),
        text(n.path("from"), "name"),
        null,
        text(n, "name"),
        text(n, "link"),
        thumb,
        instant(text(n, "created_time")),
        instant(text(n, "updated_time")),
        null,
        List.of(asset));
  }

  private MediaAsset asset(JsonNode n, int order) {
    var image = n.path("media").path("image");
    String id = text(n.path("target"), "id");
    if (id == null) id = "attachment-" + order;
    String source = text(n.path("media"), "source");
    if (source == null) source = text(image, "src");
    if (source == null) source = text(n, "url");
    return new MediaAsset(
        null,
        null,
        id,
        text(n, "type"),
        source,
        text(image, "src"),
        integer(image, "width"),
        integer(image, "height"),
        null,
        order);
  }
}
