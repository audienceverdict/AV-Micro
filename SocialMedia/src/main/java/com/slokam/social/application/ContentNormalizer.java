package com.slokam.social.application;

import com.slokam.social.core.model.*;
import java.util.HashSet;
import org.springframework.stereotype.Component;

@Component
public class ContentNormalizer {
  public SocialContent normalize(SocialAccount a, SocialContent c) {
    if (!a.id().equals(c.accountId())
        || !a.platform().equals(c.platform())
        || c.externalContentId() == null
        || c.externalContentId().isBlank()
        || c.publishedAt() == null
        || c.contentType() == null
        || c.status() == null) throw new IllegalArgumentException("Invalid normalized content");
    var ids = new HashSet<String>();
    for (var asset : c.mediaAssets())
      if (asset.externalAssetId() == null || !ids.add(asset.externalAssetId()))
        throw new IllegalArgumentException("Invalid/duplicate asset ID");
    return c;
  }
}
