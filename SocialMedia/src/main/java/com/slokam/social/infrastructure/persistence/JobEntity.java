package com.slokam.social.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "social_sync_jobs")
public class JobEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long accountId;

  @Enumerated(EnumType.STRING)
  public com.slokam.social.core.model.SyncMode syncMode;

  public Instant startedAt;
  public Instant completedAt;
  public String status;
  public long fetchedCount;
  public long insertedCount;
  public long updatedCount;
  public long skippedCount;
  public long failedCount;

  @Column(columnDefinition = "text")
  public String errorMessage;
}
