package com.slokam.social.providers.linkedin;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.ContentClassifier;
import com.slokam.social.core.spi.ContentMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "social.platforms.linkedin.enabled",
    havingValue = "true")
public class LinkedInMapper implements ContentMapper {
  private final ContentClassifier classifier;

  public LinkedInMapper(@Qualifier("linkedInClassifier") ContentClassifier c) {
    classifier = c;
  }

  public SocialContent map(SocialAccount a, JsonNode n) {
    if (!n.has("publishedAt"))
      throw new IllegalArgumentException("Missing LinkedIn publication timestamp");
    return content(
        a,
        n,
        classifier.classify(n),
        "linkedin#post",
        text(n, "author"),
        a.accountName(),
        null,
        text(n, "commentary"),
        null,
        null,
        Instant.ofEpochMilli(n.path("publishedAt").asLong()),
        n.has("lastModifiedAt") ? Instant.ofEpochMilli(n.path("lastModifiedAt").asLong()) : null,
        null,
        List.of());
  }
}
