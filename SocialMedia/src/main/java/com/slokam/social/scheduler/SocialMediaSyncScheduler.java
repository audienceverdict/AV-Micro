package com.slokam.social.scheduler;

import com.slokam.social.application.SocialMediaSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@lombok.extern.slf4j.Slf4j
@Component
@ConditionalOnProperty(name = "social.sync.enabled", havingValue = "true", matchIfMissing = true)
public class SocialMediaSyncScheduler {
  private final SocialMediaSyncService syncService;

  public SocialMediaSyncScheduler(SocialMediaSyncService service) {
    syncService = service;
  }

  @Scheduled(
      fixedDelayString = "${social.sync.interval-ms:300000}",
      initialDelayString = "${social.sync.initial-delay-ms:0}")
  public void synchronize() {
    log.info("Starting scheduled social media synchronization");
    syncService.synchronizeAllPlatforms();
  }
}
