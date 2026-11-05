package com.slokam.social.core.model;

import java.math.BigDecimal;

public record MediaAsset(
    Long id,
    Long contentId,
    String externalAssetId,
    String mediaType,
    String mediaUrl,
    String thumbnailUrl,
    Integer width,
    Integer height,
    BigDecimal durationSeconds,
    int displayOrder) {}
