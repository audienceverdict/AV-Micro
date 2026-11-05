package com.slokam.social.publishing;

import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class PublishingConfiguration {
  @Bean
  public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
    var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("social-sync-");
    return scheduler;
  }

  @Bean
  public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler publishingTaskScheduler() {
    var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("social-publishing-");
    return scheduler;
  }

  @Bean
  public TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
    return new TransactionTemplate(manager);
  }
}
