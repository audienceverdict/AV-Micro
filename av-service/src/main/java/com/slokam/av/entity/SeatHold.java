package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_holds")
public class SeatHold {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String showId;

    @Column(nullable = false, length = 80)
    public String seatId;

    @Column(nullable = false, length = 36)
    public String userId;

    @Column(nullable = false)
    public Instant heldAt = Instant.now();

    @Column(nullable = false)
    public Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SeatHoldStatus status = SeatHoldStatus.ACTIVE;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
