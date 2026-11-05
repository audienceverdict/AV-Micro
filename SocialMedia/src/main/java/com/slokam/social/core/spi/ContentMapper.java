package com.slokam.social.core.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.*;

@FunctionalInterface
public interface ContentMapper {
  SocialContent map(SocialAccount account, JsonNode node);
}
