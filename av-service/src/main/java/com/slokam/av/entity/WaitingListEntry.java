package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waiting_list_entries")
public class WaitingListEntry {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String showId;

    @Column(nullable = false, length = 36)
    public String userId;

    @Column(nullable = false)
    public int requestedSeatsCount;

    @Column(nullable = false)
    public int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public WaitingListStatus status = WaitingListStatus.WAITING;

    @Column public Instant offerExpiresAt;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
