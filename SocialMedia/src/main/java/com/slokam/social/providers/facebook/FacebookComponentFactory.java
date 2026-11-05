package com.slokam.social.providers.facebook;

import com.slokam.social.core.spi.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public record FacebookComponentFactory(
    @Qualifier("facebookAdapter") SocialPlatformAdapter adapter,
    @Qualifier("facebookClassifier") ContentClassifier classifier,
    @Qualifier("facebookMapper") ContentMapper mapper)
    implements PlatformComponentFactory {}
