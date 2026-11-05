package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String userId;

    @Column(nullable = false, length = 40)
    public String type;

    @Column(nullable = false, length = 500)
    public String message;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
