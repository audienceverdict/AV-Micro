package com.slokam.social.publishing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PublishingMediaStoreTest {
  @TempDir Path directory;
  @Test void metaMediaUrlRequiresHttpsAndUsesGatewayRoute() {
    var properties = new PublishingProperties();
    var store = new PublishingMediaStore(properties);
    String id = UUID.randomUUID().toString();
    for (String base : new String[]{"", "http://localhost:8080", "https://user:password@example.com", "https://example.com?token=secret"}) {
      properties.setPublicBaseUrl(base);
      var failure = assertThrows(PublishFailure.class, () -> store.publicUrl(id));
      assertEquals("ACTION_REQUIRED", failure.status());
      assertTrue(failure.getMessage().contains("PUBLISHING_PUBLIC_BASE_URL"));
      assertFalse(failure.getMessage().contains("secret"));
      assertFalse(failure.getMessage().contains("password"));
    }
    properties.setPublicBaseUrl("https://api.example.com/");
    properties.setPublicMediaPath("/api/v1/social/content/");
    assertEquals("https://api.example.com/api/v1/social/content/" + id + "/media", store.publicUrl(id));
  }
  @Test void storesExactBytesRejectsTraversalAndCleansUpOversizedUploads() throws Exception {
    var properties = new PublishingProperties();
    properties.setStorageDirectory(directory);
    properties.setMaxMediaBytes(3);
    var store = new PublishingMediaStore(properties);
    String id = UUID.randomUUID().toString();
    store.save(id, new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[]{1,2,3}));
    assertArrayEquals(new byte[]{1,2,3}, store.resource(id).getContentAsByteArray());
    assertThrows(ResponseStatusException.class, () -> store.path("../../application.yml"));
    String oversized = UUID.randomUUID().toString();
    assertThrows(ResponseStatusException.class,
        () -> store.save(oversized, new MockMultipartFile("file", new byte[]{1,2,3,4})));
    assertFalse(Files.exists(store.path(oversized)));
    assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(store.path(id)));
  }
}
