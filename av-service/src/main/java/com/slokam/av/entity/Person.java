package com.slokam.av.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "people", indexes = @Index(name = "idx_people_name", columnList = "fullName"))
public class Person {
    @Id @Column(length = 36)
    public String id = UUID.randomUUID().toString();
    @Column(nullable = false, length = 150)
    public String fullName;
    @Column(length = 10000)
    public String biography;
    @Column(length = 2000)
    public String profileImageUrl;

    public LocalDate dateOfBirth;
    @Column(length = 100)
    public String nationality;
    @Column(length = 2000)
    public String officialWebsite;
    @org.hibernate.annotations.BatchSize(size = 100)
    @ElementCollection @CollectionTable(name = "person_skills", joinColumns = @JoinColumn(name = "person_id")) @Column(name = "skill", length = 100)
    public Set<String> skills = new LinkedHashSet<>();
    @Column(nullable = false) public Instant createdAt = Instant.now();
    @Column(nullable = false) public Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
