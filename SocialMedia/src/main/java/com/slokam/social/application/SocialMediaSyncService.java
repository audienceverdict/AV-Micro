package com.slokam.social.application;

import com.slokam.social.core.model.SocialAccount;

public interface SocialMediaSyncService {
  void synchronizeAllPlatforms();

  void synchronizePlatform(String platform);

  void synchronizeAccount(SocialAccount account);
}
