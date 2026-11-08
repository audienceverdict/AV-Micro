package com.slokam.social;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.spi.PlatformComponentFactory;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.logging.MethodLoggingAspect;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

class ProviderProxyTest {
  @Configuration(proxyBeanMethods = false)
  @ComponentScan("com.slokam.social.providers")
  static class Providers {}

  @Test
  void createsAllProviderFactoriesWithInterfaceProxiesAndRetainsLogging() {
    Logger logger = (Logger) LoggerFactory.getLogger(MethodLoggingAspect.class);
    Level previous = logger.getLevel();
    var events = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    events.start();
    logger.addAppender(events);
    logger.setLevel(Level.DEBUG);
    try {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
          .withUserConfiguration(Providers.class)
          .withPropertyValues(
              "spring.aop.proxy-target-class=false", "social.platforms.linkedin.enabled=true")
          .withBean(MethodLoggingAspect.class)
          .withBean(SocialProperties.class)
          .withBean(ObjectMapper.class)
          .withBean(CredentialResolver.class, () -> new CredentialResolver(new MockEnvironment()))
          .withBean(ProviderHttpClient.class)
          .withBean(com.slokam.social.publishing.PublishingProperties.class)
          .withBean(com.slokam.social.publishing.PublishingHttp.class)
          .withBean(com.slokam.social.publishing.PublishingTokens.class)
          .withBean(com.slokam.social.infrastructure.credentials.YouTubeReadAccess.class)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var factories = context.getBeansOfType(PlatformComponentFactory.class);
                assertThat(factories).hasSize(4);
                for (String provider : List.of("facebook", "instagram", "youTube", "linkedIn")) {
                  var factory = factories.get(provider + "ComponentFactory");
                  assertThat(AopUtils.isJdkDynamicProxy(factory)).isTrue();
                  assertThat(factory.adapter()).isSameAs(context.getBean(provider + "Adapter"));
                  assertThat(factory.classifier())
                      .isSameAs(context.getBean(provider + "Classifier"));
                  assertThat(factory.mapper()).isSameAs(context.getBean(provider + "Mapper"));
                  assertThat(AopUtils.isJdkDynamicProxy(factory.adapter())).isTrue();
                  assertThat(AopUtils.isJdkDynamicProxy(factory.mapper())).isTrue();
                  assertThat(AopUtils.isJdkDynamicProxy(factory.classifier())).isTrue();
                }
                assertThat(events.list)
                    .anyMatch(e -> e.getFormattedMessage().contains("Method start:"));
                assertThat(events.list)
                    .anyMatch(e -> e.getFormattedMessage().contains("status=SUCCESS"));
              });
    } finally {
      logger.detachAppender(events);
      logger.setLevel(previous);
      events.stop();
    }
  }
}
