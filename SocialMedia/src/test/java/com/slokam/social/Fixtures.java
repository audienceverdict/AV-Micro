package com.slokam.social;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.slokam.social.core.model.*;
import java.time.Instant;
import java.util.List;

final class Fixtures {
  static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

  static SocialAccount account(long id) {
    return new SocialAccount(id, "test", "external" + id, "name", "TOKEN", false);
  }

  static SocialContent content(String caption) {
    return new SocialContent(
        null,
        "test",
        "post",
        1L,
        "author",
        "Author",
        ContentType.IMAGE,
        "image",
        null,
        caption,
        "https://example.org/post",
        null,
        NOW.minusSeconds(60),
        null,
        NOW,
        null,
        JsonNodeFactory.instance.objectNode().put("id", "post"),
        ContentStatus.AVAILABLE,
        List.of(
            new MediaAsset(
                null,
                null,
                "asset",
                "IMAGE",
                "https://example.org/a.jpg",
                null,
                100,
                200,
                null,
                0)));
  }

  static SocialContent copy(
      SocialContent c, com.fasterxml.jackson.databind.JsonNode metadata, List<MediaAsset> assets) {
    return new SocialContent(
        c.id(),
        c.platform(),
        c.externalContentId(),
        c.accountId(),
        c.authorId(),
        c.authorName(),
        c.contentType(),
        c.providerContentType(),
        c.title(),
        c.caption(),
        c.permalink(),
        c.thumbnailUrl(),
        c.publishedAt(),
        c.providerUpdatedAt(),
        c.retrievedAt(),
        c.engagementMetadata(),
        metadata,
        c.status(),
        assets);
  }
}
