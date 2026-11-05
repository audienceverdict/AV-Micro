package com.slokam.social.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_sync_checkpoints")
@NoArgsConstructor
public class CheckpointEntity extends TimestampedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public com.slokam.social.core.model.SyncMode syncMode;

  public Instant lastSuccessfulSyncAt;

  @Column(columnDefinition = "text")
  public String providerHighWatermark;

  @Column(columnDefinition = "text")
  public String backfillCursor;

  public boolean backfillCompleted;
  public Instant lastAttemptAt;
  public String lastStatus;
  public Instant retryNotBefore;
  public String ephemeralCursor;
  public Instant lastReconciledAt;
  public boolean windowReconciliation;
  public Instant windowFrom;
  public Instant windowTo;

  @Column(columnDefinition = "text")
  public String windowCursor;
}
