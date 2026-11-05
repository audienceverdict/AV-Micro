package com.slokam.social.application;

import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.*;
import com.slokam.social.core.registry.PlatformRegistry;
import com.slokam.social.core.spi.SocialPlatformAdapter;
import com.slokam.social.infrastructure.persistence.*;
import java.time.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DefaultSocialMediaSyncService implements SocialMediaSyncService {
  private final AccountCatalog catalog;
  private final PlatformRegistry registry;
  private final SyncCheckpointService checkpoints;
  private final ContentNormalizer normalizer;
  private final AccountLeaseService leases;
  private final SocialProperties properties;
  private final Clock clock;
  private final com.slokam.social.infrastructure.resilience.SyncTelemetry telemetry;

  public DefaultSocialMediaSyncService(
      AccountCatalog c,
      PlatformRegistry r,
      SyncCheckpointService s,
      ContentNormalizer n,
      AccountLeaseService l,
      SocialProperties p,
      Clock clock,
      com.slokam.social.infrastructure.resilience.SyncTelemetry telemetry) {
    catalog = c;
    registry = r;
    checkpoints = s;
    normalizer = n;
    leases = l;
    properties = p;
    this.clock = clock;
    this.telemetry = telemetry;
  }

  public void synchronizeAllPlatforms() {
    if (properties.getSync().isEnabled())
      catalog.enabledAccounts().forEach(this::synchronizeAccount);
  }

  public void synchronizePlatform(String platform) {
    catalog.enabledAccounts().stream()
        .filter(a -> a.platform().equals(platform))
        .forEach(this::synchronizeAccount);
  }

  public void synchronizeAccount(SocialAccount a) {
    log.info("Starting synchronization account={} platform={}", a.id(), a.platform());
    String owner = null;
    try {
      owner = leases.acquire(a.id());
      if (owner == null) {
        log.info("Account {} is already synchronizing", a.id());
        return;
      }
      var adapter = registry.resolve(a.platform());
      if (coolingDown(a)) return;
      if (adapter.capabilities().stories() && a.storiesEnabled())
        refreshEphemeral(a, adapter, owner);
      if (coolingDown(a)) return;
      var cp = checkpoints.read(a.id(), SyncMode.INCREMENTAL);
      if (cp.lastSuccessfulSyncAt == null) run(a, adapter, SyncMode.INITIAL, owner);
      else {
        if (cp.windowCursor != null) refreshHead(a, adapter, owner);
        if (coolingDown(a)) return;
        run(a, adapter, SyncMode.INCREMENTAL, owner);
      }
      // A failed historical page does not prevent future incremental traversal, or vice versa.
      if (coolingDown(a)) return;
      var backfill = checkpoints.read(a.id(), SyncMode.BACKFILL);
      if (backfill.id != null && !backfill.backfillCompleted)
        run(a, adapter, SyncMode.BACKFILL, owner);
    } catch (Exception e) {
      log.error("Account {} synchronization could not proceed: {}", a.id(), safeError(e));
    } finally {
      log.info("Finished synchronization attempt account={} platform={}", a.id(), a.platform());
      if (owner != null)
        try {
          leases.release(a.id(), owner);
        } catch (Exception e) {
          log.error("Account {} lease release failed: {}", a.id(), safeError(e));
        }
    }
  }

  private boolean coolingDown(SocialAccount a) {
    var cp = checkpoints.read(a.id(), SyncMode.INCREMENTAL);
    if (cp.retryNotBefore != null && cp.retryNotBefore.isAfter(clock.instant())) {
      log.info("Account {} deferred until {} by provider Retry-After", a.id(), cp.retryNotBefore);
      return true;
    }
    return false;
  }

  private void recordFailure(JobEntity job, String owner, Exception e) {
    log.error(
        "Synchronization failed account={} job={} mode={} reason={}",
        job.accountId,
        job.id,
        job.syncMode,
        safeError(e));
    report(checkpoints.finish(job.id, owner, "FAILED", safeError(e)));
    if (e instanceof com.slokam.social.core.exception.ProviderException pe
        && pe.retryAfter() != null
        && !pe.retryAfter().isNegative()
        && !pe.retryAfter().isZero())
      checkpoints.defer(job.accountId, owner, clock.instant().plus(pe.retryAfter()));
  }

  private void refreshEphemeral(SocialAccount a, SocialPlatformAdapter adapter, String owner) {
    var job = checkpoints.start(a.id(), SyncMode.INCREMENTAL, owner);
    boolean more = false;
    try {
      String cursor = checkpoints.read(a.id(), SyncMode.INCREMENTAL).ephemeralCursor;
      var seen = new HashSet<String>();
      if (cursor != null) seen.add(cursor);
      for (int i = 0; i < properties.getSync().getIncrementalBatchPages(); i++) {
        var query =
            new ContentQuery(
                a.id(),
                null,
                clock.instant(),
                properties.getSync().getPageSize(),
                cursor,
                Set.of(ContentType.STORY),
                SyncMode.INCREMENTAL);
        log.debug(
            "Fetching page account={} platform={} mode={} page={}",
            a.id(),
            a.platform(),
            query.mode(),
            i + 1);
        var page = adapter.fetchContent(a, query);
        if (page.hasMore() && !seen.add(page.nextCursor()))
          throw new IllegalArgumentException("Repeated ephemeral cursor");
        var records = page.content().stream().map(c -> normalizer.normalize(a, c)).toList();
        checkpoints.commitEphemeralPage(job.id, owner, records, page.nextCursor());
        cursor = page.nextCursor();
        more = page.hasMore();
        if (!more) break;
      }
      report(checkpoints.finish(job.id, owner, more ? "PENDING" : "REFRESHED", null));
    } catch (Exception e) {
      recordFailure(job, owner, e);
    }
  }

  private void refreshHead(SocialAccount a, SocialPlatformAdapter adapter, String owner) {
    var job = checkpoints.start(a.id(), SyncMode.INCREMENTAL, owner);
    try {
      var q =
          new ContentQuery(
              a.id(),
              null,
              clock.instant(),
              properties.getSync().getPageSize(),
              null,
              adapter.capabilities().contentTypes(),
              SyncMode.INCREMENTAL);
      var page = adapter.fetchContent(a, q);
      var records = page.content().stream().map(c -> normalizer.normalize(a, c)).toList();
      checkpoints.commitRefresh(job.id, owner, records);
      report(checkpoints.finish(job.id, owner, "REFRESHED", null));
    } catch (Exception e) {
      recordFailure(job, owner, e);
    }
  }

  private void run(SocialAccount a, SocialPlatformAdapter adapter, SyncMode mode, String owner) {
    JobEntity job = checkpoints.start(a.id(), mode, owner);
    boolean more = false;
    try {
      if (a.credentialExpiresAt() != null && !a.credentialExpiresAt().isAfter(clock.instant()))
        throw new com.slokam.social.core.exception.ProviderException(
            "Configured provider credential is expired", false, null);
      var cfg = properties.getSync();
      Instant from = null, to = clock.instant();
      String cursor = null;
      int budget;
      if (mode == SyncMode.INCREMENTAL) {
        var c = checkpoints.window(a.id(), owner, cfg.getOverlapSeconds());
        from = c.windowFrom;
        to = c.windowTo;
        cursor = c.windowCursor;
        budget = cfg.getIncrementalBatchPages();
      } else if (mode == SyncMode.BACKFILL) {
        var c = checkpoints.read(a.id(), mode);
        to = c.windowTo;
        cursor = c.backfillCursor;
        budget = cfg.getBackfillBatchPages();
      } else
        budget = 1; // Exactly one initial page; continuation belongs to BACKFILL, never INITIAL.
      var seen = new HashSet<String>();
      if (cursor != null) seen.add(cursor);
      for (int i = 0; i < budget; i++) {
        leases.guard(a.id(), owner);
        var query =
            new ContentQuery(
                a.id(),
                from,
                to,
                cfg.getPageSize(),
                cursor,
                adapter.capabilities().contentTypes(),
                mode);
        log.debug(
            "Fetching page account={} platform={} mode={} page={}",
            a.id(),
            a.platform(),
            query.mode(),
            i + 1);
        var page = adapter.fetchContent(a, query);
        if (page == null) throw new IllegalArgumentException("Empty provider response");
        if (page.hasMore() && !seen.add(page.nextCursor()))
          throw new IllegalArgumentException("Repeated provider cursor");
        // APIs lacking reliable update filters are also reconciled during traversal.
        // Newer-than-boundary items are deferred.
        final Instant boundary = to;
        final Instant lower = from;
        var records =
            page.content().stream()
                .map(c -> normalizer.normalize(a, c))
                .filter(c -> !c.publishedAt().isAfter(boundary))
                .filter(
                    c ->
                        lower == null
                            || !c.publishedAt().isBefore(lower)
                            || (c.providerUpdatedAt() != null
                                && !c.providerUpdatedAt().isBefore(lower)))
                .toList();
        checkpoints.commitPage(job.id, owner, records, page, query);
        log.debug(
            "Committed page account={} job={} mode={} fetched={} retained={} hasMore={}",
            a.id(),
            job.id,
            mode,
            page.content().size(),
            records.size(),
            page.hasMore());
        cursor = page.nextCursor();
        more = page.hasMore();
        if (!more || mode == SyncMode.INITIAL) break;
      }
      report(checkpoints.finish(job.id, owner, more ? "PENDING" : "SUCCESS", null));
    } catch (Exception e) {
      try {
        recordFailure(job, owner, e);
      } catch (Exception failure) {
        log.error(
            "Account {} job {} finalization failed; checkpoint transaction rolled back",
            a.id(),
            job.id);
        log.error(
            "Finalization failure type={} reason={}",
            failure.getClass().getSimpleName(),
            safeError(failure));
      }
    }
  }

  private void report(JobEntity j) {
    telemetry.completed(j);
    log.info(
        "Account {} mode={} status={} durationMs={} fetched={} inserted={} updated={} skipped={} failed={}",
        j.accountId,
        j.syncMode,
        j.status,
        Duration.between(j.startedAt, j.completedAt).toMillis(),
        j.fetchedCount,
        j.insertedCount,
        j.updatedCount,
        j.skippedCount,
        j.failedCount);
  }

  private String safeError(Exception e) {
    if (e instanceof com.slokam.social.core.exception.ProviderException) return e.getMessage();
    return e.getClass().getSimpleName()
        + ": synchronization failed (credentials and provider bodies omitted)";
  }
}
