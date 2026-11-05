package com.slokam.social.core.spi;

public interface PlatformComponentFactory {
  SocialPlatformAdapter adapter();

  ContentClassifier classifier();

  ContentMapper mapper();
}
