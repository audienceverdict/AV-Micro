package com.slokam.social.core.registry;

import com.slokam.social.core.spi.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PlatformRegistry {
  private final Map<String, SocialPlatformAdapter> adapters = new HashMap<>();

  public PlatformRegistry(List<SocialPlatformAdapter> beans) {
    for (var bean : beans)
      if (adapters.putIfAbsent(bean.platformId(), bean) != null)
        throw new IllegalStateException("Duplicate platform: " + bean.platformId());
  }

  public SocialPlatformAdapter resolve(String id) {
    var adapter = adapters.get(id);
    if (adapter == null) throw new IllegalArgumentException("Unregistered platform: " + id);
    return adapter;
  }
}
