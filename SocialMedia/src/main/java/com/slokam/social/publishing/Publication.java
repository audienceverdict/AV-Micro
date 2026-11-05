package com.slokam.social.publishing;

public record Publication(
    String id, String contentId, String platform, String accountId, String stage,
    String remoteReference, String externalId, String title, String caption,
    String contentType, String mediaType, long mediaSize, String privacy) {}
