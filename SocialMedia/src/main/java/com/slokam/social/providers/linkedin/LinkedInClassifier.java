package com.slokam.social.providers.linkedin;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.ContentType;
import com.slokam.social.core.spi.ContentClassifier;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "social.platforms.linkedin.enabled",
    havingValue = "true")
public class LinkedInClassifier implements ContentClassifier {
  public ContentType classify(JsonNode n) {
    var content = n.path("content");
    return content.isMissingNode() || content.isEmpty() ? ContentType.TEXT : ContentType.UNKNOWN;
  }
}
