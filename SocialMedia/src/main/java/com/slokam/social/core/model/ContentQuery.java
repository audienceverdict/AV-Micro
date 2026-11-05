package com.slokam.social.core.model;

import java.time.Instant;
import java.util.Set;

public record ContentQuery(
    Long accountId,
    Instant from,
    Instant to,
    int pageSize,
    String cursor,
    Set<ContentType> requestedTypes,
    SyncMode mode) {}
