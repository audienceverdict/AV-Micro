package com.slokam.social.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class TimestampedEntity {
  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  @PrePersist
  void create() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
