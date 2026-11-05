package com.slokam.social.providers.youtube;

import com.slokam.social.core.spi.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public record YouTubeComponentFactory(
    @Qualifier("youTubeAdapter") SocialPlatformAdapter adapter,
    @Qualifier("youTubeClassifier") ContentClassifier classifier,
    @Qualifier("youTubeMapper") ContentMapper mapper)
    implements PlatformComponentFactory {}
