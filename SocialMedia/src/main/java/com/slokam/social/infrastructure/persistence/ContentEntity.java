package com.slokam.social.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_content")
@NoArgsConstructor
public class ContentEntity extends TimestampedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long accountId;

  @Column(nullable = false)
  public String platform;

  @Column(nullable = false)
  public String externalContentId;

  @Enumerated(EnumType.STRING)
  public com.slokam.social.core.model.ContentType contentType;

  public String providerContentType;
  public String authorId;
  public String authorName;

  @Column(columnDefinition = "text")
  public String title;

  @Column(columnDefinition = "longtext")
  public String caption;

  @Column(columnDefinition = "text")
  public String permalink;

  @Column(columnDefinition = "text")
  public String thumbnailUrl;

  public Instant publishedAt;
  public Instant providerUpdatedAt;
  public Instant retrievedAt;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(columnDefinition = "json")
  public com.fasterxml.jackson.databind.JsonNode engagementMetadata;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(columnDefinition = "json")
  public com.fasterxml.jackson.databind.JsonNode providerMetadata;

  @Enumerated(EnumType.STRING)
  public com.slokam.social.core.model.ContentStatus contentStatus;

  public String fingerprint;
}
