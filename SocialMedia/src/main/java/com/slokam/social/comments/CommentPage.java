package com.slokam.social.comments;

import java.time.Instant;
import java.util.List;

public record CommentPage(List<Comment> comments, String nextCursor) {
  public record Comment(
      String id,
      String parentId,
      String authorId,
      String authorName,
      String text,
      Instant publishedAt,
      Instant updatedAt,
      Long likes,
      Long replies) {}
}
