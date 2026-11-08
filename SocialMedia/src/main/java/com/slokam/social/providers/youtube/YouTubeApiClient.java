package com.slokam.social.providers.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.infrastructure.credentials.YouTubeReadAccess;
import com.slokam.social.core.model.SocialAccount;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class YouTubeApiClient {
  private final YouTubeReadAccess access;

  public YouTubeApiClient(YouTubeReadAccess access) {
    this.access = access;
  }

  public JsonNode get(SocialAccount a, String operation, Map<String, String> query) {
    return access.get(a, operation, query);
  }

  public JsonNode channel(SocialAccount a) {
    return get(
        a,
        "channels",
        Map.of("part", "snippet,contentDetails,statistics", "id", a.externalAccountId()));
  }

  public JsonNode playlists(SocialAccount a, String cursor, int size) {
    var q =
        new HashMap<>(
            Map.of(
                "part",
                "snippet,contentDetails",
                "channelId",
                a.externalAccountId(),
                "maxResults",
                String.valueOf(Math.min(50, size))));
    if (cursor != null) q.put("pageToken", cursor);
    return get(a, "playlists", q);
  }

  public JsonNode playlistItems(SocialAccount a, String playlist, String cursor, int size) {
    var q =
        new HashMap<>(
            Map.of(
                "part",
                "snippet,contentDetails",
                "playlistId",
                playlist,
                "maxResults",
                String.valueOf(Math.min(50, size))));
    if (cursor != null) q.put("pageToken", cursor);
    return get(a, "playlistItems", q);
  }

  public JsonNode videos(SocialAccount a, String ids) {
    return get(
        a,
        "videos",
        Map.of("part", "snippet,contentDetails,statistics,liveStreamingDetails,status", "id", ids));
  }
}
