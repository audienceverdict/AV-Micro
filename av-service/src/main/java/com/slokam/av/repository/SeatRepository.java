package com.slokam.av.repository;

import com.slokam.av.entity.Seat;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface SeatRepository extends JpaRepository<Seat, String> {
    List<Seat> findByScreenId(String screenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :ids order by s.id")
    List<Seat> lockAllByIds(@Param("ids") Collection<String> ids);
}
