package com.slokam.social.core.model;

import java.util.*;

public record PageResult<T>(
    List<T> content, String nextCursor, boolean hasMore, Map<String, String> requestMetadata) {
  public PageResult {
    content = List.copyOf(content);
    requestMetadata = Map.copyOf(requestMetadata);
    if (hasMore && (nextCursor == null || nextCursor.isBlank()))
      throw new IllegalArgumentException("Missing next cursor");
  }
}
