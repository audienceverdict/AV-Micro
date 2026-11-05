package com.slokam.social;

import com.slokam.social.configuration.SocialProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SocialProperties.class)
public class SocialSyncApplication {
  public static void main(String[] args) {
    SpringApplication.run(SocialSyncApplication.class, args);
  }
}
