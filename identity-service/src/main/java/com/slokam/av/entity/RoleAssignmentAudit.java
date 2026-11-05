package com.slokam.av.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_assignment_audits")
public class RoleAssignmentAudit {
    @Id @Column(length = 36)
    public String id = UUID.randomUUID().toString();
    @Column(nullable = false, length = 36)
    public String actorId;
    @Column(nullable = false, length = 36)
    public String userId;
    @Lob @Column(nullable = false)
    public String previousAssignments;
    @Lob @Column(nullable = false)
    public String newAssignments;
    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
