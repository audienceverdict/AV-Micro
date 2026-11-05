package com.slokam.av.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "person_social_links")
public class PersonSocialLink {
    @Id @Column(length = 36)
    public String id = UUID.randomUUID().toString();
    @Column(nullable = false, length = 36)
    public String personId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    public SocialPlatform platform;
    @Column(nullable = false, length = 2000)
    public String profileUrl;
    @Column(length = 150)
    public String username;
    @Column(nullable = false)
    public boolean isVerifiedOfficial = false;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    @Column(nullable = false) public Instant updatedAt = Instant.now();
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
