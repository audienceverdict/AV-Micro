package com.slokam.social.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_credentials")
@NoArgsConstructor
public class CredentialEntity extends TimestampedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public Long accountId;

  @Column(nullable = false)
  public String credentialReference;

  public Instant expiresAt;
}
