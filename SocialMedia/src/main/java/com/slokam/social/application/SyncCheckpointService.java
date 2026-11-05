package com.slokam.social.application;

import com.slokam.social.core.model.*;
import com.slokam.social.infrastructure.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncCheckpointService {
  private final CheckpointRepository checkpoints;
  private final JobRepository jobs;
  private final ContentPersistenceService persistence;
  private final AccountLeaseService leases;
  private final Clock clock;
  private final com.slokam.social.configuration.SocialProperties properties;

  public SyncCheckpointService(
      CheckpointRepository c,
      JobRepository j,
      ContentPersistenceService p,
      AccountLeaseService l,
      Clock clock,
      com.slokam.social.configuration.SocialProperties properties) {
    checkpoints = c;
    jobs = j;
    persistence = p;
    leases = l;
    this.clock = clock;
    this.properties = properties;
  }

  private CheckpointEntity load(Long id, SyncMode mode) {
    return checkpoints
        .findByAccountIdAndSyncMode(id, mode)
        .orElseGet(
            () -> {
              var c = new CheckpointEntity();
              c.accountId = id;
              c.syncMode = mode;
              return c;
            });
  }

  @Transactional(readOnly = true)
  public CheckpointEntity read(Long id, SyncMode mode) {
    return load(id, mode);
  }

  @Transactional
  public JobEntity start(Long id, SyncMode mode, String owner) {
    leases.guard(id, owner);
    var job = new JobEntity();
    job.accountId = id;
    job.syncMode = mode;
    job.startedAt = clock.instant();
    job.status = "RUNNING";
    return jobs.saveAndFlush(job);
  }

  @Transactional
  public CheckpointEntity window(Long id, String owner, long overlap) {
    leases.guard(id, owner);
    var c = load(id, SyncMode.INCREMENTAL);
    if (c.windowTo == null) {
      c.windowTo = clock.instant();
      c.windowFrom =
          c.lastSuccessfulSyncAt == null ? null : c.lastSuccessfulSyncAt.minusSeconds(overlap);
      c.windowReconciliation =
          c.lastReconciledAt != null
              && !c.lastReconciledAt
                  .plusSeconds(properties.getSync().getReconciliationIntervalSeconds())
                  .isAfter(c.windowTo);
      if (c.windowReconciliation) c.windowFrom = null;
      c.windowCursor = null;
    }
    c.lastAttemptAt = clock.instant();
    c.lastStatus = "RUNNING";
    return checkpoints.saveAndFlush(c);
  }

  @Transactional
  public void commitPage(
      Long jobId,
      String owner,
      List<SocialContent> records,
      PageResult<SocialContent> page,
      ContentQuery query) {
    var job = jobs.findById(jobId).orElseThrow();
    leases.guard(job.accountId, owner);
    var counts = persistence.persist(records);
    job.fetchedCount += page.content().size();
    job.insertedCount += counts.inserted();
    job.updatedCount += counts.updated();
    job.skippedCount += counts.skipped();
    if (query.mode() == SyncMode.INITIAL) {
      var incremental = load(job.accountId, SyncMode.INCREMENTAL);
      // The oldest returned timestamp is a conservative baseline. The following incremental
      // traversal is exhaustive.
      incremental.lastSuccessfulSyncAt =
          records.stream()
              .map(SocialContent::publishedAt)
              .min(Comparator.naturalOrder())
              .orElse(query.to());
      incremental.lastReconciledAt = query.to();
      incremental.lastStatus = "SUCCESS";
      checkpoints.save(incremental);
      var backfill = load(job.accountId, SyncMode.BACKFILL);
      backfill.windowTo = query.to();
      backfill.backfillCursor = page.nextCursor();
      backfill.backfillCompleted = !page.hasMore();
      backfill.lastStatus = "PENDING";
      checkpoints.save(backfill);
    } else if (query.mode() == SyncMode.BACKFILL) {
      var c = load(job.accountId, SyncMode.BACKFILL);
      c.backfillCursor = page.nextCursor();
      c.backfillCompleted = !page.hasMore();
      c.lastAttemptAt = clock.instant();
      c.lastStatus = page.hasMore() ? "PENDING" : "SUCCESS";
      if (!page.hasMore()) c.lastSuccessfulSyncAt = query.to();
      checkpoints.save(c);
    } else {
      var c = load(job.accountId, SyncMode.INCREMENTAL);
      c.windowCursor = page.nextCursor();
      c.lastStatus = page.hasMore() ? "PENDING" : "SUCCESS";
      if (!page.hasMore()) {
        if (c.windowReconciliation) c.lastReconciledAt = c.windowTo;
        c.lastSuccessfulSyncAt = c.windowTo;
        c.providerHighWatermark = c.windowTo.toString();
        c.windowFrom = null;
        c.windowTo = null;
        c.windowCursor = null;
      }
      checkpoints.save(c);
    }
    jobs.save(job);
  }

  @Transactional
  public void commitRefresh(Long jobId, String owner, List<SocialContent> records) {
    var job = jobs.findById(jobId).orElseThrow();
    leases.guard(job.accountId, owner);
    var c = persistence.persist(records);
    job.fetchedCount += records.size();
    job.insertedCount += c.inserted();
    job.updatedCount += c.updated();
    job.skippedCount += c.skipped();
    jobs.save(job);
  }

  @Transactional
  public void defer(Long id, String owner, Instant until) {
    leases.guard(id, owner);
    var cp = load(id, SyncMode.INCREMENTAL);
    if (cp.retryNotBefore == null || until.isAfter(cp.retryNotBefore)) cp.retryNotBefore = until;
    checkpoints.save(cp);
  }

  @Transactional
  public void commitEphemeralPage(
      Long jobId, String owner, List<SocialContent> records, String cursor) {
    commitRefresh(jobId, owner, records);
    var job = jobs.findById(jobId).orElseThrow();
    var cp = load(job.accountId, SyncMode.INCREMENTAL);
    cp.ephemeralCursor = cursor;
    checkpoints.save(cp);
  }

  @Transactional
  public JobEntity finish(Long jobId, String owner, String status, String error) {
    var job = jobs.findById(jobId).orElseThrow();
    leases.guard(job.accountId, owner);
    job.completedAt = clock.instant();
    job.status = status;
    job.errorMessage = error;
    if ("FAILED".equals(status)) job.failedCount++;
    if ("FAILED".equals(status)) {
      var cp =
          load(
              job.accountId,
              job.syncMode == SyncMode.INITIAL ? SyncMode.INCREMENTAL : job.syncMode);
      cp.lastAttemptAt = clock.instant();
      cp.lastStatus = status;
      checkpoints.save(cp);
    }
    return jobs.save(job);
  }
}
