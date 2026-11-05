package com.slokam.av.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "screens")
public class Screen {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String theatreId;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(nullable = false)
    public int number;

    @Column(nullable = false, name = "row_count")
    public int rows;

    @Column(nullable = false)
    public int seatsPerRow;

    @Column(nullable = false)
    public int layoutVersion = 1;

    @Column(length = 36)
    public String activeLayoutVersionId;

    @Column(nullable = false)
    public boolean layoutLocked = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ActiveStatus status = ActiveStatus.ACTIVE;

    @OneToMany(
            mappedBy = "screen",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("rowNumber,columnNumber")
    public List<Seat> seats = new ArrayList<>();

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
