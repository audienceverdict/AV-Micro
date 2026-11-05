package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.social.comments.*;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.providers.facebook.FacebookApiClient;
import com.slokam.social.providers.instagram.InstagramApiClient;
import com.slokam.social.providers.youtube.YouTubeApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommentProviderTest {
  final YouTubeApiClient yt = mock(YouTubeApiClient.class);
  final InstagramApiClient ig = mock(InstagramApiClient.class);
  final FacebookApiClient fb = mock(FacebookApiClient.class);
  final OfficialCommentProvider provider = new OfficialCommentProvider(yt, ig, fb);
  final ObjectMapper json = new ObjectMapper();

  SocialAccount account(String platform) {
    return new SocialAccount(1L, platform, "channel", "name", "TOKEN", false, null);
  }

  @Test
  void youtubeUsesSeparateReplyPaginationAndPreservesIdentity() throws Exception {
    when(yt.get(any(), eq("commentThreads"), anyMap()))
        .thenReturn(
            json.readTree(
                """
   {"items":[{"id":"thread","snippet":{"totalReplyCount":8,"topLevelComment":{"id":"parent","snippet":{"textDisplay":"hello","publishedAt":"2026-10-01T00:00:00Z","likeCount":0}}}}],"nextPageToken":"next"}
   """));
    var page = provider.fetch(account("youtube"), "video", "", null, 100);
    assertEquals("parent", page.comments().getFirst().id());
    assertEquals(8, page.comments().getFirst().replies());
    assertEquals("next", page.nextCursor());
    when(yt.get(any(), eq("comments"), anyMap()))
        .thenReturn(
            json.readTree(
                """
   {"items":[{"id":"reply","snippet":{"textDisplay":"reply"}}],"nextPageToken":"more-replies"}
   """));
    var replies = provider.fetch(account("youtube"), "video", "parent", "reply-page", 100);
    assertEquals("parent", replies.comments().getFirst().parentId());
    assertEquals("more-replies", replies.nextCursor());
    verify(yt)
        .get(
            any(),
            eq("comments"),
            eq(
                Map.of(
                    "part",
                    "snippet",
                    "maxResults",
                    "100",
                    "textFormat",
                    "plainText",
                    "parentId",
                    "parent",
                    "pageToken",
                    "reply-page")));
  }

  @Test
  void instagramSchedulesReplyReadsEvenWhenCountIsNotAvailable() throws Exception {
    when(ig.objectEdge(any(), eq("media"), eq("comments"), anyString(), isNull(), eq(100)))
        .thenReturn(
            json.readTree(
                """
   {"data":[{"id":"p","text":"హలో","timestamp":"2026-10-01T00:00:00Z"}]}
   """));
    var page = provider.fetch(account("instagram"), "media", "", null, 100);
    assertEquals("హలో", page.comments().getFirst().text());
    assertNull(page.comments().getFirst().replies());
    when(ig.objectEdge(any(), eq("p"), eq("replies"), anyString(), eq("cursor"), eq(100)))
        .thenReturn(json.readTree("{\"data\":[]}"));
    assertTrue(
        provider.fetch(account("instagram"), "media", "p", "cursor", 100).comments().isEmpty());
  }

  @Test
  void malformedResponsesCannotAdvanceProgress() throws Exception {
    when(yt.get(any(), anyString(), anyMap())).thenReturn(json.readTree("{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> provider.fetch(account("youtube"), "v", "", null, 100));
  }
}
