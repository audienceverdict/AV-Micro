package com.slokam.social.comments;

import static com.slokam.social.providers.JsonSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.providers.*;
import com.slokam.social.providers.facebook.FacebookApiClient;
import com.slokam.social.providers.instagram.InstagramApiClient;
import com.slokam.social.providers.youtube.YouTubeApiClient;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OfficialCommentProvider implements CommentProvider {
  private final YouTubeApiClient youtube;
  private final InstagramApiClient instagram;
  private final FacebookApiClient facebook;

  public OfficialCommentProvider(YouTubeApiClient y, InstagramApiClient i, FacebookApiClient f) {
    youtube = y;
    instagram = i;
    facebook = f;
  }

  public boolean supports(String p) {
    return Set.of("youtube", "instagram", "facebook").contains(p);
  }

  public CommentPage fetch(
      SocialAccount a, String content, String parent, String cursor, int size) {
    boolean root = parent == null || parent.isEmpty();
    var result = new ArrayList<CommentPage.Comment>();
    if (a.platform().equals("youtube")) {
      var q = new HashMap<String, String>();
      q.put("part", "snippet");
      q.put("maxResults", String.valueOf(Math.min(size, 100)));
      q.put("textFormat", "plainText");
      q.put(root ? "videoId" : "parentId", root ? content : parent);
      if (root) q.put("order", "time");
      if (cursor != null) q.put("pageToken", cursor);
      var page = youtube.get(a, root ? "commentThreads" : "comments", q);
      for (var item : array(page, "items")) {
        var c = root ? item.path("snippet").path("topLevelComment") : item;
        var n = c.path("snippet");
        result.add(
            new CommentPage.Comment(
                requiredId(c),
                root ? null : parent,
                text(n.path("authorChannelId"), "value"),
                text(n, "authorDisplayName"),
                text(n, "textDisplay"),
                instant(text(n, "publishedAt")),
                instant(text(n, "updatedAt")),
                number(n, "likeCount"),
                root ? number(item.path("snippet"), "totalReplyCount") : null));
      }
      return new CommentPage(result, text(page, "nextPageToken"));
    }
    GraphApiClient client = a.platform().equals("instagram") ? instagram : facebook;
    boolean ig = a.platform().equals("instagram");
    String fields =
        ig
            ? "id,text,timestamp,from,like_count"
            : "id,message,created_time,from,like_count,comment_count";
    var page =
        client.objectEdge(
            a, root ? content : parent, ig && !root ? "replies" : "comments", fields, cursor, size);
    for (var n : array(page, "data"))
      result.add(
          new CommentPage.Comment(
              requiredId(n),
              root ? null : parent,
              text(n.path("from"), "id"),
              text(n.path("from"), ig ? "username" : "name"),
              text(n, ig ? "text" : "message"),
              instant(text(n, ig ? "timestamp" : "created_time")),
              null,
              number(n, "like_count"),
              root && !ig ? number(n, "comment_count") : null));
    return new CommentPage(result, client.next(page));
  }

  private static Long number(JsonNode n, String f) {
    return n.hasNonNull(f) ? n.path(f).asLong() : null;
  }

  private static String requiredId(JsonNode n) {
    var id = text(n, "id");
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Comment ID missing");
    return id;
  }
}
