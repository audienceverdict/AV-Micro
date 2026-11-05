package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.slokam.social.application.*;
import com.slokam.social.infrastructure.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ContentPersistenceServiceTest {
  @Test
  void insertsSkipsUpdatesAndReconcilesAssets() {
    var posts = mock(ContentRepository.class);
    var assets = mock(AssetRepository.class);
    var state = new HashMap<String, ContentEntity>();
    var children = new HashMap<String, AssetEntity>();
    when(posts.findByPlatformAndAccountIdAndExternalContentId(any(), any(), any()))
        .thenAnswer(i -> Optional.ofNullable(state.get(i.getArgument(2))));
    when(posts.saveAndFlush(any()))
        .thenAnswer(
            i -> {
              ContentEntity e = i.getArgument(0);
              e.id = 1L;
              state.put(e.externalContentId, e);
              return e;
            });
    when(assets.findByContentId(1L)).thenAnswer(i -> new ArrayList<>(children.values()));
    when(assets.save(any()))
        .thenAnswer(
            i -> {
              AssetEntity e = i.getArgument(0);
              children.put(e.externalAssetId, e);
              return e;
            });
    var service =
        new ContentPersistenceService(
            posts, assets, JsonMapper.builder().findAndAddModules().build());
    assertEquals(1, service.persist(List.of(Fixtures.content("one"))).inserted());
    assertEquals(1, service.persist(List.of(Fixtures.content("one"))).skipped());
    assertEquals(1, service.persist(List.of(Fixtures.content("two"))).updated());
    assertEquals(1, state.size());
    assertEquals(1, children.size());
    assertEquals("two", state.get("post").caption);
    assertEquals("https://example.org/a.jpg", children.get("asset").mediaUrl);
  }
}
