package com.slokam.social.core.spi;

import com.slokam.social.core.capability.PlatformCapabilities;
import com.slokam.social.core.model.*;

public interface SocialPlatformAdapter {
  String platformId();

  PlatformCapabilities capabilities();

  PageResult<SocialContent> fetchContent(SocialAccount account, ContentQuery query);
}
