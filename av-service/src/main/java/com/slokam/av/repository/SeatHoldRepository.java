package com.slokam.av.repository;

import com.slokam.av.entity.SeatHold;
import com.slokam.av.entity.SeatHoldStatus;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface SeatHoldRepository extends JpaRepository<SeatHold, String> {
    List<SeatHold> findByShowId(String showId);

    List<SeatHold> findByUserIdAndStatusAndExpiresAtAfter(
            String userId, SeatHoldStatus status, Instant now);

    List<SeatHold> findByShowIdAndStatusAndExpiresAtAfter(
            String showId, SeatHoldStatus status, Instant now);

    @Query("select h from SeatHold h where h.status=:status and h.expiresAt<=:now")
    List<SeatHold> expired(@Param("status") SeatHoldStatus status, @Param("now") Instant now);

    @Modifying
    @Query(
            "delete from SeatHold h where h.showId=:showId and h.seatId in :seatIds and"
                    + " h.status=:status")
    int deleteOld(
            @Param("showId") String showId,
            @Param("seatIds") Collection<String> seatIds,
            @Param("status") SeatHoldStatus status);
}
