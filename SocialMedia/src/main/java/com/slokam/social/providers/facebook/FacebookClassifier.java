package com.slokam.social.providers.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.ContentType;
import com.slokam.social.core.spi.ContentClassifier;
import org.springframework.stereotype.Component;

@Component
public class FacebookClassifier implements ContentClassifier {
  public ContentType classify(JsonNode n) {
    var assets = n.path("attachments");
    if (assets.isEmpty()) return ContentType.TEXT;
    var first = assets.path(0);
    if (first.path("subattachments").path("data").size() > 1 || assets.size() > 1)
      return ContentType.CAROUSEL;
    return switch (first.path("type").asText()) {
      case "photo" -> ContentType.IMAGE;
      case "video_inline", "video", "video_autoplay" -> ContentType.VIDEO;
      case "reel", "reels" -> ContentType.REEL;
      case "live_video" -> ContentType.LIVE;
      default -> ContentType.UNKNOWN;
    };
  }
}
