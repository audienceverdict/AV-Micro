package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.slokam.social.infrastructure.logging.MethodLoggingAspect;
import com.slokam.social.publishing.PublishFailure;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class MethodLoggingTest {
  public static class Operation {
    public void publish() {
      throw new PublishFailure("ACTION_REQUIRED", "Configure PUBLISHING_PUBLIC_BASE_URL");
    }
    public String run(boolean fail, String credential) {
      if (fail) throw new IllegalArgumentException("secret=" + credential);
      return credential;
    }
  }

  @Test
  void tracesSuccessAndFailureWithoutExposingArgumentsResultsOrExceptionMessages() {
    Logger logger = (Logger) LoggerFactory.getLogger(MethodLoggingAspect.class);
    Level previous = logger.getLevel();
    var events = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    events.start();
    logger.addAppender(events);
    logger.setLevel(Level.DEBUG);
    try {
      var factory = new AspectJProxyFactory(new Operation());
      factory.addAspect(new MethodLoggingAspect());
      Operation proxy = factory.getProxy();
      assertEquals("sensitive-token", proxy.run(false, "sensitive-token"));
      assertThrows(IllegalArgumentException.class, () -> proxy.run(true, "sensitive-token"));
      assertThrows(PublishFailure.class, proxy::publish);
      var messages = events.list.stream().map(e -> e.getFormattedMessage()).toList();
      assertTrue(messages.stream().anyMatch(m -> m.contains("Method start:")));
      assertTrue(messages.stream().anyMatch(m -> m.contains("status=SUCCESS")));
      assertTrue(messages.stream().anyMatch(m -> m.contains("status=FAILED")));
      assertTrue(
          messages.stream().anyMatch(m -> m.contains("exceptionType=IllegalArgumentException")));
      assertFalse(messages.stream().anyMatch(m -> m.contains("sensitive-token")));
      assertTrue(messages.stream().anyMatch(m -> m.contains("reason=Configure PUBLISHING_PUBLIC_BASE_URL")));
      assertTrue(events.list.stream().allMatch(e -> e.getThrowableProxy() == null));
    } finally {
      logger.detachAppender(events);
      logger.setLevel(previous);
      events.stop();
    }
  }
}
