package com.slokam.av.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "movie_credits", uniqueConstraints = @UniqueConstraint(name = "uk_credit_role", columnNames = {"movieId", "personId", "roleKey"}), indexes = {@Index(name = "idx_credit_movie_order", columnList = "movieId,billingOrder"), @Index(name = "idx_credit_person", columnList = "personId")})
public class MovieCredit {
    @Id @Column(length = 36)
    public String id = UUID.randomUUID().toString();
    @Column(nullable = false, length = 36)
    public String movieId;
    @Column(nullable = false, length = 36)
    public String personId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    public CreditType creditType;
    @Column(length = 100)
    public String department;
    @Column(nullable = false, length = 150)
    public String roleTitle;
    @Column(length = 150)
    public String characterName;
    @Column(length = 2000)
    public String characterDescription;
    @Column(length = 2000)
    public String characterImageUrl;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    public CharacterCategory characterCategory;
    @Column(length = 150)
    public String characterOccupation;
    @Column(length = 10000)
    public String characterRelationships;

    public Integer screenTimeMinutes;

    public Integer billingOrder;
    @Column(nullable = false)
    public boolean isMainCast = false;
    @Column(nullable = false)
    public boolean isCameo = false;
    @Column(nullable = false)
    public boolean isVoiceRole = false;
    @Column(nullable = false, length = 64)
    public String roleKey;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    @Column(nullable = false) public Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
