package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "layout_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"screen_id", "version_number"}))
public class LayoutVersion {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String screenId;

    @Column(nullable = false)
    public int versionNumber;

    @Column(nullable = false, length = 120)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public LayoutVersionStatus status = LayoutVersionStatus.DRAFT;

    // Only ACTIVE versions carry this value. NULL permits multiple drafts/archives.
    @Column(name = "active_screen_id", length = 36, unique = true)
    public String activeScreenId;

    public Instant publishedAt;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    public String snapshot;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
    @PrePersist
    @PreUpdate
    void syncActiveScreen() {
        activeScreenId = status == LayoutVersionStatus.ACTIVE ? screenId : null;
    }

}
