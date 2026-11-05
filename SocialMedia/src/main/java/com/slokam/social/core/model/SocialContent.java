package com.slokam.social.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record SocialContent(
    Long id,
    String platform,
    String externalContentId,
    Long accountId,
    String authorId,
    String authorName,
    ContentType contentType,
    String providerContentType,
    String title,
    String caption,
    String permalink,
    String thumbnailUrl,
    Instant publishedAt,
    Instant providerUpdatedAt,
    Instant retrievedAt,
    JsonNode engagementMetadata,
    JsonNode providerMetadata,
    ContentStatus status,
    List<MediaAsset> mediaAssets) {}
