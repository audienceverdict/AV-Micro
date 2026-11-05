package com.slokam.social.core.capability;

import com.slokam.social.core.model.ContentType;
import java.util.Set;

public record PlatformCapabilities(
    Set<ContentType> contentTypes,
    boolean serverSideDateFilter,
    boolean exposesUpdates,
    boolean stories) {}
