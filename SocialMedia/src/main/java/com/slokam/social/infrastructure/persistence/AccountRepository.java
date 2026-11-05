package com.slokam.social.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
  List<AccountEntity> findByEnabledTrue();

  Optional<AccountEntity> findByPlatformAndExternalAccountId(
      String platform, String externalAccountId);
}
