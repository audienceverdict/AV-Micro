package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 150)
    public String name = "Movie lover";

    @Column(nullable = false, unique = true, length = 20)
    public String mobile;

    @Column(length = 20)
    public String alternativeMobile;

    @Column(unique = true)
    public String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Role role = Role.USER;

    // The legacy primary role remains authoritative for existing clients.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    public java.util.Set<Role> additionalRoles = new java.util.HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_verifier_shows", joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "show_id"}))
    @Column(name = "show_id", nullable = false, length = 36)
    public java.util.Set<String> verifierShowIds = new java.util.HashSet<>();

    public java.util.Set<Role> effectiveRoles() {
        var roles = java.util.EnumSet.of(role);
        roles.addAll(additionalRoles);
        return java.util.Collections.unmodifiableSet(roles);
    }

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    public Instant mobileVerifiedAt;

    public Instant emailVerifiedAt;

    @PreUpdate
    void updateTime() {
        updatedAt = Instant.now();
    }
}
