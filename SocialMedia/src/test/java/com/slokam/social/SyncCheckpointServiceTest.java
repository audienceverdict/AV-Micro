package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.slokam.social.application.*;
import com.slokam.social.core.model.*;
import com.slokam.social.infrastructure.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SyncCheckpointServiceTest {
  CheckpointRepository cps;
  JobRepository jobs;
  ContentPersistenceService persistence;
  AccountLeaseService leases;
  SyncCheckpointService service;
  Map<SyncMode, CheckpointEntity> state;
  JobEntity job;

  @BeforeEach
  void setup() {
    cps = mock(CheckpointRepository.class);
    jobs = mock(JobRepository.class);
    persistence = mock(ContentPersistenceService.class);
    leases = mock(AccountLeaseService.class);
    state = new HashMap<>();
    job = new JobEntity();
    job.id = 10L;
    job.accountId = 1L;
    when(cps.findByAccountIdAndSyncMode(eq(1L), any()))
        .thenAnswer(i -> Optional.ofNullable(state.get(i.getArgument(1))));
    when(cps.save(any()))
        .thenAnswer(
            i -> {
              CheckpointEntity c = i.getArgument(0);
              state.put(c.syncMode, c);
              return c;
            });
    when(cps.saveAndFlush(any()))
        .thenAnswer(
            i -> {
              CheckpointEntity c = i.getArgument(0);
              state.put(c.syncMode, c);
              return c;
            });
    when(jobs.findById(10L)).thenReturn(Optional.of(job));
    when(persistence.persist(any())).thenReturn(new ContentPersistenceService.Counts(1, 0, 0));
    service =
        new SyncCheckpointService(
            cps,
            jobs,
            persistence,
            leases,
            Clock.fixed(Fixtures.NOW, ZoneOffset.UTC),
            new com.slokam.social.configuration.SocialProperties());
  }

  ContentQuery query(SyncMode mode, String cursor) {
    return new ContentQuery(1L, null, Fixtures.NOW, 100, cursor, Set.of(ContentType.IMAGE), mode);
  }

  @Test
  void firstPageCreatesIndependentHistoryAndIncrementalBaseline() {
    service.commitPage(
        10L,
        "owner",
        List.of(Fixtures.content("new")),
        new PageResult<>(List.of(Fixtures.content("new")), "next", true, Map.of()),
        query(SyncMode.INITIAL, null));
    assertEquals("next", state.get(SyncMode.BACKFILL).backfillCursor);
    assertFalse(state.get(SyncMode.BACKFILL).backfillCompleted);
    assertEquals(
        Fixtures.NOW.minusSeconds(60), state.get(SyncMode.INCREMENTAL).lastSuccessfulSyncAt);
  }

  @Test
  void resumesMissedWindowsAndOnlyAdvancesOnLastPage() {
    var c = new CheckpointEntity();
    c.accountId = 1L;
    c.syncMode = SyncMode.INCREMENTAL;
    c.lastSuccessfulSyncAt = Fixtures.NOW.minusSeconds(3600);
    state.put(c.syncMode, c);
    service.window(1L, "owner", 120);
    assertEquals(Fixtures.NOW.minusSeconds(3720), c.windowFrom);
    assertEquals(Fixtures.NOW, c.windowTo);
    service.commitPage(
        10L,
        "owner",
        List.of(),
        new PageResult<>(List.of(), "next", true, Map.of()),
        query(SyncMode.INCREMENTAL, null));
    assertEquals(Fixtures.NOW.minusSeconds(3600), c.lastSuccessfulSyncAt);
    assertEquals("next", c.windowCursor);
    service.window(1L, "owner", 120);
    assertEquals("next", c.windowCursor);
    service.commitPage(
        10L,
        "owner",
        List.of(),
        new PageResult<>(List.of(), null, false, Map.of()),
        query(SyncMode.INCREMENTAL, "next"));
    assertEquals(Fixtures.NOW, c.lastSuccessfulSyncAt);
    assertNull(c.windowTo);
    assertNull(c.windowCursor);
  }

  @Test
  void databaseFailurePreventsCheckpointAndJobAdvancement() {
    service.window(1L, "owner", 120);
    when(persistence.persist(any())).thenThrow(new IllegalStateException("database unavailable"));
    assertThrows(
        IllegalStateException.class,
        () ->
            service.commitPage(
                10L,
                "owner",
                List.of(),
                new PageResult<>(List.of(), null, false, Map.of()),
                query(SyncMode.INCREMENTAL, null)));
    assertNull(state.get(SyncMode.INCREMENTAL).lastSuccessfulSyncAt);
    assertEquals(0, job.fetchedCount);
  }

  @Test
  void backfillProgressDoesNotAlterIncrementalCheckpoint() {
    var c = new CheckpointEntity();
    c.accountId = 1L;
    c.syncMode = SyncMode.INCREMENTAL;
    c.lastSuccessfulSyncAt = Fixtures.NOW;
    state.put(c.syncMode, c);
    service.commitPage(
        10L,
        "owner",
        List.of(),
        new PageResult<>(List.of(), "page3", true, Map.of()),
        query(SyncMode.BACKFILL, "page2"));
    assertEquals("page3", state.get(SyncMode.BACKFILL).backfillCursor);
    assertEquals(Fixtures.NOW, c.lastSuccessfulSyncAt);
    service.commitPage(
        10L,
        "owner",
        List.of(),
        new PageResult<>(List.of(), null, false, Map.of()),
        query(SyncMode.BACKFILL, "page3"));
    assertTrue(state.get(SyncMode.BACKFILL).backfillCompleted);
  }

  @Test
  void dailyReconciliationUsesAnUnfilteredWindowAndResetsItsTimer() {
    var c = new CheckpointEntity();
    c.accountId = 1L;
    c.syncMode = SyncMode.INCREMENTAL;
    c.lastSuccessfulSyncAt = Fixtures.NOW.minusSeconds(300);
    c.lastReconciledAt = Fixtures.NOW.minusSeconds(86401);
    state.put(c.syncMode, c);
    service.window(1L, "owner", 120);
    assertTrue(c.windowReconciliation);
    assertNull(c.windowFrom);
    service.commitPage(
        10L,
        "owner",
        List.of(),
        new PageResult<>(List.of(), null, false, Map.of()),
        query(SyncMode.INCREMENTAL, null));
    assertEquals(Fixtures.NOW, c.lastReconciledAt);
  }

  @Test
  void ephemeralProgressAndCooldownDoNotAdvanceDurableContentCheckpoint() {
    service.commitEphemeralPage(10L, "owner", List.of(), "story-next");
    var c = state.get(SyncMode.INCREMENTAL);
    assertEquals("story-next", c.ephemeralCursor);
    assertNull(c.lastSuccessfulSyncAt);
    service.defer(1L, "owner", Fixtures.NOW.plusSeconds(600));
    service.defer(1L, "owner", Fixtures.NOW.plusSeconds(300));
    assertEquals(Fixtures.NOW.plusSeconds(600), c.retryNotBefore);
  }
}
