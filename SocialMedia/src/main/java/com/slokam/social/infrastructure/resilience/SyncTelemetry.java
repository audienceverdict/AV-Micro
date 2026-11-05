package com.slokam.social.infrastructure.resilience;

import com.slokam.social.infrastructure.persistence.JobEntity;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SyncTelemetry {
  private final MeterRegistry meters;

  public SyncTelemetry(MeterRegistry meters) {
    this.meters = meters;
  }

  public void completed(JobEntity j) {
    String mode = j.syncMode == null ? "UNKNOWN" : j.syncMode.name();
    String status = j.status;
    meters.counter("social.sync.jobs", "mode", mode, "status", status).increment();
    meters
        .timer("social.sync.duration", "mode", mode, "status", status)
        .record(Duration.between(j.startedAt, j.completedAt));
    count(mode, "fetched", j.fetchedCount);
    count(mode, "inserted", j.insertedCount);
    count(mode, "updated", j.updatedCount);
    count(mode, "skipped", j.skippedCount);
    count(mode, "failed", j.failedCount);
  }

  private void count(String mode, String disposition, long count) {
    meters
        .counter("social.sync.records", "mode", mode, "disposition", disposition)
        .increment(count);
  }
}
