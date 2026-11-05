package com.slokam.social.providers.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.ContentType;
import com.slokam.social.core.spi.ContentClassifier;
import org.springframework.stereotype.Component;

@Component
public class InstagramClassifier implements ContentClassifier {
  public ContentType classify(JsonNode n) {
    if (n.path("_story").asBoolean()) return ContentType.STORY;
    if ("REELS".equals(n.path("media_product_type").asText())) return ContentType.REEL;
    return switch (n.path("media_type").asText()) {
      case "IMAGE" -> ContentType.IMAGE;
      case "VIDEO" -> ContentType.VIDEO;
      case "CAROUSEL_ALBUM" -> ContentType.CAROUSEL;
      default -> ContentType.UNKNOWN;
    };
  }
}
