package com.slokam.av.repository;

import com.slokam.av.entity.LayoutVersion;
import com.slokam.av.entity.LayoutVersionStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface LayoutVersionRepository extends JpaRepository<LayoutVersion, String> {
    List<LayoutVersion> findByScreenIdOrderByVersionNumberDesc(String screenId);

    Optional<LayoutVersion> findByScreenIdAndVersionNumber(String screenId, int version);

    Optional<LayoutVersion> findFirstByScreenIdAndStatusOrderByVersionNumberDesc(
            String screenId, LayoutVersionStatus status);
}
