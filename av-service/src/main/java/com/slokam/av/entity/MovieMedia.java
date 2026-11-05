package com.slokam.av.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "movie_media", indexes = @Index(name = "idx_media_movie_order", columnList = "movieId,displayOrder"))
public class MovieMedia {
    @Id @Column(length = 36)
    public String id = UUID.randomUUID().toString();
    @Column(nullable = false, length = 36)
    public String movieId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    public MovieMediaType mediaType;
    @Column(length = 200)
    public String title;
    @Column(length = 2000)
    public String description;
    @Column(nullable = false, length = 2000)
    public String mediaUrl;
    @Column(length = 2000)
    public String thumbnailUrl;
    @Column(length = 80)
    public String language;
    @Column(nullable = false)
    public boolean isOfficial = false;
    @Column(nullable = false)
    public boolean isPrimary = false;
    @Column(nullable = false)
    public int displayOrder = 0;
    @Column(length = 80)
    public String sourcePlatform;

    public Instant publishedAt;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    @Column(nullable = false) public Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
