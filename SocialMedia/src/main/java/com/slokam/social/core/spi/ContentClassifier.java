package com.slokam.social.core.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.ContentType;

@FunctionalInterface
public interface ContentClassifier {
  ContentType classify(JsonNode node);
}
