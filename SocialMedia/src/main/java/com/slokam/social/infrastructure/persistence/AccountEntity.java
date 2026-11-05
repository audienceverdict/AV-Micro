package com.slokam.social.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_accounts")
@NoArgsConstructor
public class AccountEntity extends TimestampedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String platform;

  @Column(nullable = false)
  public String externalAccountId;

  public String accountName;
  public boolean enabled;
}
