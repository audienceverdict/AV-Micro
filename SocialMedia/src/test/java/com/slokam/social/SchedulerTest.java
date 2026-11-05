package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.slokam.social.application.SocialMediaSyncService;
import com.slokam.social.scheduler.SocialMediaSyncScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class SchedulerTest {
  @Test
  void runsImmediatelyWithFiveMinuteFixedDelay() throws Exception {
    var a = SocialMediaSyncScheduler.class.getMethod("synchronize").getAnnotation(Scheduled.class);
    assertEquals("${social.sync.interval-ms:300000}", a.fixedDelayString());
    assertEquals("${social.sync.initial-delay-ms:0}", a.initialDelayString());
    var s = mock(SocialMediaSyncService.class);
    new SocialMediaSyncScheduler(s).synchronize();
    verify(s).synchronizeAllPlatforms();
  }
}
