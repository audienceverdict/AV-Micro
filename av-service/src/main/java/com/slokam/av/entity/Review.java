package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String movieId;

    @Column(length = 36)
    public String userId;

    @Column(nullable = false, length = 150)
    public String author;

    @Column(nullable = false, length = 180)
    public String title;

    @Column(nullable = false, length = 2000)
    public String text;

    @Column(nullable = false)
    public int rating;

    @Column(length = 500)
    public String videoUrl;

    @Column(length = 80)
    public String platform;

    @Column(length = 500)
    public String thumbnail;

    @Column(nullable = false)
    public boolean isHighlighted = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ReviewStatus status = ReviewStatus.PENDING;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
