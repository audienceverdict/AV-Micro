package com.slokam.social.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<ContentEntity, Long> {
  Optional<ContentEntity> findByPlatformAndAccountIdAndExternalContentId(
      String platform, Long accountId, String externalContentId);
}
