package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "theatres")
public class Theatre {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 180)
    public String name;

    @Column(nullable = false, length = 500)
    public String address;

    @Column(nullable = false, length = 100)
    public String city;

    @Column(nullable = false, length = 100)
    public String state;

    @Column(length = 80)
    public String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ActiveStatus status = ActiveStatus.ACTIVE;

    @Column(length = 500)
    public String mapUrl;

    @OneToMany(
            mappedBy = "theatre",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    public List<VenueMedia> media = new ArrayList<>();

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
