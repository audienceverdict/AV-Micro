package com.slokam.av.repository;

import com.slokam.av.entity.Show;
import com.slokam.av.entity.ShowStatus;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface ShowRepository extends JpaRepository<Show, String> {
    boolean existsByLayoutVersionId(String versionId);
    List<Show> findByScreenId(String screenId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Show s where s.id=:id")
    Optional<Show> lockById(@Param("id") String id);

    List<Show> findByMovieIdAndDateGreaterThanEqual(String movieId, LocalDate date);

    List<Show> findByTheatreIdAndDate(String theatreId, LocalDate date);

    @Query(
            "select s from Show s where s.screenId=:screenId and s.date=:date and"
                    + " s.status<>:cancelled and s.startTime < :end and s.endTime > :start")
    List<Show> overlapping(
            @Param("screenId") String screenId,
            @Param("date") LocalDate date,
            @Param("start") LocalTime start,
            @Param("end") LocalTime end,
            @Param("cancelled") ShowStatus cancelled);
}
