package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.slokam.social.core.registry.PlatformRegistry;
import com.slokam.social.core.spi.SocialPlatformAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformRegistryTest {
  @Test
  void discoversAndRejectsDuplicates() {
    var a = mock(SocialPlatformAdapter.class);
    when(a.platformId()).thenReturn("custom");
    var r = new PlatformRegistry(List.of(a));
    assertSame(a, r.resolve("custom"));
    assertThrows(IllegalArgumentException.class, () -> r.resolve("missing"));
    assertThrows(IllegalStateException.class, () -> new PlatformRegistry(List.of(a, a)));
  }
}
