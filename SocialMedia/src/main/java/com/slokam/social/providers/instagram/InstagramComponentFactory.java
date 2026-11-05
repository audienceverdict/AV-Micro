package com.slokam.social.providers.instagram;

import com.slokam.social.core.spi.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public record InstagramComponentFactory(
    @Qualifier("instagramAdapter") SocialPlatformAdapter adapter,
    @Qualifier("instagramClassifier") ContentClassifier classifier,
    @Qualifier("instagramMapper") ContentMapper mapper)
    implements PlatformComponentFactory {}
