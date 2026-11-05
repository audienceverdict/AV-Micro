package com.slokam.social.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_media_assets")
@NoArgsConstructor
public class AssetEntity extends TimestampedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long contentId;

  @Column(nullable = false)
  public String externalAssetId;

  public String mediaType;

  @Column(columnDefinition = "text")
  public String mediaUrl;

  @Column(columnDefinition = "text")
  public String thumbnailUrl;

  public Integer width;
  public Integer height;

  @Column(precision = 18, scale = 6)
  public java.math.BigDecimal durationSeconds;

  public int displayOrder;
}
