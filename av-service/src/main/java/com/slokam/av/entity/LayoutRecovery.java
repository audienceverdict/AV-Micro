package com.slokam.av.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only provenance for explicit legacy repairs. Original seats/versions are retained. */
@Entity
@Table(name = "layout_recoveries")
public class LayoutRecovery {
    @Id @Column(length = 36) public String id = UUID.randomUUID().toString();
    @Column(length = 36, nullable = false) public String screenId;
    @Column(length = 36) public String showId;
    @Column(length = 36) public String previousVersionId;
    @Column(length = 36, nullable = false) public String recoveredVersionId;
    @Column(length = 100, nullable = false) public String source;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
