package com.slokam.social.configuration;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class WorkerConfiguration {
  @Bean
  public Clock clock() {
    return Clock.tick(Clock.systemUTC(), java.time.Duration.ofNanos(1000));
  }
}
