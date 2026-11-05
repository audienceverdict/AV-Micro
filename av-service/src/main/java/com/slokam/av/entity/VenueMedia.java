package com.slokam.av.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "venue_media")
public class VenueMedia {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "theatre_id")
    public Theatre theatre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public MediaType type = MediaType.IMAGE;

    @Column(nullable = false, length = 500)
    public String url;

    @Column(length = 180)
    public String title;
}
