package com.slokam.social.providers.youtube;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.*;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.capability.PlatformCapabilities;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class YouTubeAdapter implements SocialPlatformAdapter {
  private final YouTubeApiClient client;
  private final ContentMapper mapper;
  private final ContentMapper playlists;
  private final ObjectMapper json;
  private final SocialProperties properties;

  public YouTubeAdapter(
      YouTubeApiClient c,
      @Qualifier("youTubeMapper") ContentMapper m,
      @Qualifier("youTubePlaylistMapper") ContentMapper p,
      ObjectMapper j,
      SocialProperties properties) {
    client = c;
    mapper = m;
    playlists = p;
    json = j;
    this.properties = properties;
  }

  public String platformId() {
    return "youtube";
  }

  public PlatformCapabilities capabilities() {
    return new PlatformCapabilities(
        Set.of(ContentType.VIDEO, ContentType.LIVE, ContentType.UNKNOWN), false, false, false);
  }

  public PageResult<SocialContent> fetchContent(SocialAccount a, ContentQuery q) {
    var channels = array(client.channel(a), "items");
    if (channels.isEmpty()) throw new IllegalArgumentException("Channel is unavailable");
    var channel = channels.getFirst();
    var state = YouTubeCursor.decode(q.cursor(), json);
    var result = new ArrayList<SocialContent>();
    YouTubeCursor next = null;
    if (state.stage().equals("uploads")) {
      String uploads = text(channel.path("contentDetails").path("relatedPlaylists"), "uploads");
      if (uploads == null) throw new IllegalArgumentException("Missing uploads playlist");
      var page = client.playlistItems(a, uploads, state.page(), q.pageSize());
      addVideos(a, page, channel, result);
      String token = text(page, "nextPageToken");
      if (token != null) next = new YouTubeCursor("uploads", token, 0, null);
      else if (properties.getPlatforms().get("youtube").getAccounts().stream()
          .anyMatch(c -> c.getExternalId().equals(a.externalAccountId()) && c.isIncludePlaylists()))
        next = new YouTubeCursor("playlists", null, 0, null);
    } else if (state.stage().equals("playlists")) {
      var listing = client.playlists(a, state.page(), q.pageSize());
      var entries = array(listing, "items");
      if (state.index() < entries.size()) {
        var playlist = entries.get(state.index());
        if (state.items() == null) {
          var enriched = playlist.deepCopy();
          ((com.fasterxml.jackson.databind.node.ObjectNode) enriched)
              .set("channelProfile", channel);
          result.add(playlists.map(a, enriched));
        }
        var page = client.playlistItems(a, text(playlist, "id"), state.items(), q.pageSize());
        addVideos(a, page, channel, result);
        String token = text(page, "nextPageToken");
        if (token != null)
          next = new YouTubeCursor("playlists", state.page(), state.index(), token);
        else if (state.index() + 1 < entries.size())
          next = new YouTubeCursor("playlists", state.page(), state.index() + 1, null);
        else if (text(listing, "nextPageToken") != null)
          next = new YouTubeCursor("playlists", text(listing, "nextPageToken"), 0, null);
      } else if (text(listing, "nextPageToken") != null)
        next = new YouTubeCursor("playlists", text(listing, "nextPageToken"), 0, null);
    } else throw new IllegalArgumentException("Unknown YouTube cursor stage");
    return new PageResult<>(
        result,
        next == null ? null : next.encode(json),
        next != null,
        Map.of("operation", state.stage()));
  }

  private void addVideos(
      SocialAccount a, JsonNode page, JsonNode channel, List<SocialContent> result) {
    var ids =
        array(page, "items").stream()
            .map(n -> text(n.path("contentDetails"), "videoId"))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (!ids.isEmpty())
      for (var n : array(client.videos(a, String.join(",", ids)), "items")) {
        var enriched = n.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) enriched).set("channelProfile", channel);
        result.add(mapper.map(a, enriched));
      }
  }
}
