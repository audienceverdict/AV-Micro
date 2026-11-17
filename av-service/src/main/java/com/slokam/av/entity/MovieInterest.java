package com.slokam.av.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "movie_interests", uniqueConstraints =
        @UniqueConstraint(name = "uk_movie_interest_user", columnNames = {"movie_id", "user_id"}))
public class MovieInterest {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(name = "movie_id", nullable = false, length = 36)
    public String movieId;

    @Column(name = "user_id", nullable = false, length = 36)
    public String userId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
