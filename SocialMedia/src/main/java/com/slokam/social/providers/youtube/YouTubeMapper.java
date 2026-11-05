package com.slokam.social.providers.youtube;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.ContentClassifier;
import com.slokam.social.core.spi.ContentMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class YouTubeMapper implements ContentMapper {
  private final ContentClassifier classifier;

  public YouTubeMapper(@Qualifier("youTubeClassifier") ContentClassifier c) {
    classifier = c;
  }

  public SocialContent map(SocialAccount a, JsonNode n) {
    var s = n.path("snippet");
    String id = text(n, "id");
    String url = "https://www.youtube.com/watch?v=" + id;
    String thumb = thumbnail(n);
    String duration = text(n.path("contentDetails"), "duration");
    BigDecimal seconds =
        duration == null ? null : BigDecimal.valueOf(Duration.parse(duration).toMillis(), 3);
    var asset = new MediaAsset(null, null, id, "VIDEO", url, thumb, null, null, seconds, 0);
    return content(
        a,
        n,
        classifier.classify(n),
        "youtube#video",
        text(s, "channelId"),
        text(s, "channelTitle"),
        text(s, "title"),
        text(s, "description"),
        url,
        thumb,
        instant(text(s, "publishedAt")),
        null,
        n.get("statistics"),
        List.of(asset));
  }
}
