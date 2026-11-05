package com.slokam.social.core.model;

public record SocialAccount(
    Long id,
    String platform,
    String externalAccountId,
    String accountName,
    String credentialReference,
    boolean storiesEnabled,
    java.time.Instant credentialExpiresAt) {
  public SocialAccount(
      Long id,
      String platform,
      String externalAccountId,
      String accountName,
      String credentialReference,
      boolean storiesEnabled) {
    this(id, platform, externalAccountId, accountName, credentialReference, storiesEnabled, null);
  }
}
