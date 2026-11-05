package com.slokam.social.providers.linkedin;

import com.slokam.social.core.spi.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "social.platforms.linkedin.enabled",
    havingValue = "true")
public record LinkedInComponentFactory(
    @Qualifier("linkedInAdapter") SocialPlatformAdapter adapter,
    @Qualifier("linkedInClassifier") ContentClassifier classifier,
    @Qualifier("linkedInMapper") ContentMapper mapper)
    implements PlatformComponentFactory {}
