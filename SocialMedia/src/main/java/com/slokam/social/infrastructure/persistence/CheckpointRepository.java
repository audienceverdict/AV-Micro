package com.slokam.social.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckpointRepository extends JpaRepository<CheckpointEntity, Long> {
  Optional<CheckpointEntity> findByAccountIdAndSyncMode(
      Long accountId, com.slokam.social.core.model.SyncMode mode);
}
