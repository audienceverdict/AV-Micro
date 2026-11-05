package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.slokam.social.application.*;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.capability.*;
import com.slokam.social.core.model.*;
import com.slokam.social.core.registry.*;
import com.slokam.social.core.spi.*;
import com.slokam.social.infrastructure.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SyncServiceTest {
  AccountCatalog catalog;
  SocialPlatformAdapter adapter;
  SyncCheckpointService checkpoints;
  AccountLeaseService leases;
  SocialProperties properties;
  DefaultSocialMediaSyncService service;

  @BeforeEach
  void setup() {
    catalog = mock(AccountCatalog.class);
    adapter = mock(SocialPlatformAdapter.class);
    checkpoints = mock(SyncCheckpointService.class);
    leases = mock(AccountLeaseService.class);
    properties = new SocialProperties();
    when(adapter.platformId()).thenReturn("test");
    when(adapter.capabilities())
        .thenReturn(new PlatformCapabilities(Set.of(ContentType.IMAGE), false, false, false));
    when(leases.acquire(anyLong())).thenReturn("owner");
    when(checkpoints.read(anyLong(), any())).thenAnswer(i -> new CheckpointEntity());
    when(checkpoints.start(anyLong(), any(), any()))
        .thenAnswer(
            i -> {
              var j = new JobEntity();
              j.id = i.getArgument(0);
              j.accountId = j.id;
              j.syncMode = i.getArgument(1);
              return j;
            });
    when(checkpoints.finish(anyLong(), any(), any(), nullable(String.class)))
        .thenAnswer(
            i -> {
              var j = new JobEntity();
              j.id = i.getArgument(0);
              j.accountId = j.id;
              j.startedAt = Fixtures.NOW;
              j.completedAt = Fixtures.NOW;
              j.status = i.getArgument(2);
              return j;
            });
    service =
        new DefaultSocialMediaSyncService(
            catalog,
            new PlatformRegistry(List.of(adapter)),
            checkpoints,
            new ContentNormalizer(),
            leases,
            properties,
            Clock.fixed(Fixtures.NOW, ZoneOffset.UTC),
            new com.slokam.social.infrastructure.resilience.SyncTelemetry(
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
  }

  @Test
  void isolatesAccountFailuresAndReleasesLeases() {
    var a = Fixtures.account(1);
    var b = Fixtures.account(2);
    when(catalog.enabledAccounts()).thenReturn(List.of(a, b));
    when(adapter.fetchContent(eq(a), any())).thenThrow(new IllegalStateException("failure"));
    when(adapter.fetchContent(eq(b), any()))
        .thenReturn(new PageResult<>(List.of(), null, false, Map.of()));
    service.synchronizeAllPlatforms();
    verify(adapter).fetchContent(eq(b), any());
    verify(checkpoints).finish(eq(1L), eq("owner"), eq("FAILED"), anyString());
    verify(leases).release(1L, "owner");
    verify(leases).release(2L, "owner");
  }

  @Test
  void sameAccountLockPreventsOverlap() {
    when(leases.acquire(1L)).thenReturn(null);
    service.synchronizeAccount(Fixtures.account(1));
    verify(adapter, never()).fetchContent(any(), any());
  }

  @Test
  void repeatedCursorFailsWithoutCommittingOffendingPage() {
    var c = new CheckpointEntity();
    c.lastSuccessfulSyncAt = Fixtures.NOW.minusSeconds(300);
    when(checkpoints.read(1L, SyncMode.INCREMENTAL)).thenReturn(c);
    c.windowTo = Fixtures.NOW;
    c.windowCursor = "same";
    when(checkpoints.window(1L, "owner", 120)).thenReturn(c);
    when(adapter.fetchContent(any(), any()))
        .thenReturn(new PageResult<>(List.of(), "same", true, Map.of()));
    service.synchronizeAccount(Fixtures.account(1));
    verify(checkpoints, never()).commitPage(anyLong(), any(), any(), any(), any());
    verify(checkpoints).finish(eq(1L), eq("owner"), eq("FAILED"), anyString());
  }

  @Test
  void boundedPaginationPersistsEverySuccessfulPage() {
    properties.getSync().setIncrementalBatchPages(2);
    var c = new CheckpointEntity();
    c.lastSuccessfulSyncAt = Fixtures.NOW.minusSeconds(300);
    c.windowTo = Fixtures.NOW;
    when(checkpoints.read(1L, SyncMode.INCREMENTAL)).thenReturn(c);
    when(checkpoints.window(1L, "owner", 120)).thenReturn(c);
    when(adapter.fetchContent(any(), any()))
        .thenReturn(
            new PageResult<>(List.of(), "second", true, Map.of()),
            new PageResult<>(List.of(), "third", true, Map.of()));
    service.synchronizeAccount(Fixtures.account(1));
    verify(checkpoints, times(2)).commitPage(anyLong(), any(), any(), any(), any());
    verify(checkpoints).finish(1L, "owner", "PENDING", null);
  }

  @Test
  void providerCooldownSkipsWorkWithoutChangingCheckpoints() {
    var c = new CheckpointEntity();
    c.retryNotBefore = Fixtures.NOW.plusSeconds(600);
    when(checkpoints.read(1L, SyncMode.INCREMENTAL)).thenReturn(c);
    service.synchronizeAccount(Fixtures.account(1));
    verify(adapter, never()).fetchContent(any(), any());
    verify(checkpoints, never()).start(anyLong(), any(), any());
    verify(leases).release(1L, "owner");
  }

  @Test
  void providerFailurePersistsCooldownForLaterTicks() {
    when(adapter.fetchContent(any(), any()))
        .thenThrow(
            new com.slokam.social.core.exception.ProviderException(
                "Provider rate limit", false, Duration.ofSeconds(600)));
    service.synchronizeAccount(Fixtures.account(1));
    verify(checkpoints).defer(1L, "owner", Fixtures.NOW.plusSeconds(600));
  }
}
