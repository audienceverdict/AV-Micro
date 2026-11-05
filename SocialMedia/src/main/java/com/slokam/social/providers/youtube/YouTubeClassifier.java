package com.slokam.social.providers.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.ContentType;
import com.slokam.social.core.spi.ContentClassifier;
import org.springframework.stereotype.Component;

@Component
public class YouTubeClassifier implements ContentClassifier {
  public ContentType classify(JsonNode n) {
    return n.has("liveStreamingDetails")
            || !"none".equals(n.path("snippet").path("liveBroadcastContent").asText("none"))
        ? ContentType.LIVE
        : ContentType.VIDEO;
  }
}
