package com.slokam.social.providers.youtube;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.ContentMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class YouTubePlaylistMapper implements ContentMapper {
  public SocialContent map(SocialAccount a, JsonNode node) {
    var n = node.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("id", "playlist:" + text(node, "id"));
    var s = n.path("snippet");
    return content(
        a,
        n,
        ContentType.UNKNOWN,
        "youtube#playlist",
        text(s, "channelId"),
        text(s, "channelTitle"),
        text(s, "title"),
        text(s, "description"),
        "https://www.youtube.com/playlist?list=" + text(node, "id"),
        thumbnail(n),
        instant(text(s, "publishedAt")),
        null,
        null,
        List.of());
  }
}
