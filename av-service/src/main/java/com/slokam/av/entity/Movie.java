package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "movies")
public class Movie {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 200)
    public String title;

    @Column(length = 2000)
    public String posterUrl;

    @org.hibernate.annotations.BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "movie_posters", joinColumns = @JoinColumn(name = "movie_id"))
    @OrderColumn(name = "poster_order")
    @Column(name = "poster_url", nullable = false, columnDefinition = "MEDIUMTEXT")
    public List<String> posterImages = new ArrayList<>();

    @Column(length = 2000)
    public String backdropUrl;

    @Column(length = 2000)
    public String trailerUrl;

    @Column(length = 2000)
    public String description;

    @org.hibernate.annotations.BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "genre", length = 80)
    public List<String> genre = new ArrayList<>();

    @Column(nullable = false, length = 80)
    public String language;

    @Column(nullable = false)
    public int duration;

    public LocalDate releaseDate;

    @Column(length = 30)
    public String certification;

    @Column(length = 150)
    public String director;

    @org.hibernate.annotations.BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "movie_cast", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "cast_name", length = 150)
    public List<String> cast = new ArrayList<>();

    @Column(length = 150)
    public String production;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public MovieStatus status = MovieStatus.UPCOMING;

    @Column(length = 200)
    public String originalTitle;

    @Column(length = 300)
    public String tagline;

    @Column(length = 80)
    public String originalLanguage;

    @ElementCollection @CollectionTable(name = "movie_languages", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "language_name", length = 80)
    @org.hibernate.annotations.BatchSize(size = 100)
    public Set<String> languages = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING) @Column(length = 30)
    public ReleaseStatus releaseStatus;

    @Enumerated(EnumType.STRING) @Column(length = 30)
    public ProductionStatus productionStatus;

    @Column(length = 100)
    public String countryOfOrigin;

    @ElementCollection @CollectionTable(name = "movie_filming_locations", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "location_name", length = 200)
    @org.hibernate.annotations.BatchSize(size = 100)
    public Set<String> filmingLocations = new LinkedHashSet<>();

    @ElementCollection @CollectionTable(name = "movie_production_companies", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "company_name", length = 150)
    @org.hibernate.annotations.BatchSize(size = 100)
    public Set<String> productionCompanies = new LinkedHashSet<>();

    @Column(length = 150)
    public String distributor;

    @Column(length = 2000)
    public String officialWebsite;


    public LocalDate announcementDate;


    public LocalDate productionStartDate;


    public LocalDate productionEndDate;

    @Column(precision = 19, scale = 2)
    public BigDecimal budget;

    @Column(length = 3)
    public String currencyCode;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
