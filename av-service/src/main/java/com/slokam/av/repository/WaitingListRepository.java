package com.slokam.av.repository;

import com.slokam.av.entity.WaitingListEntry;
import com.slokam.av.entity.WaitingListStatus;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface WaitingListRepository extends JpaRepository<WaitingListEntry, String> {
    List<WaitingListEntry> findByShowIdAndStatusOrderByPositionAscCreatedAtAsc(
            String showId, WaitingListStatus status);
}
