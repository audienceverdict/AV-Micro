package com.slokam.social.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<AssetEntity, Long> {
  List<AssetEntity> findByContentId(Long contentId);
}
