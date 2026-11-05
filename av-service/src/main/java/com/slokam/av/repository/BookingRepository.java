package com.slokam.av.repository;

import com.slokam.av.entity.Booking;
import com.slokam.av.entity.BookingStatus;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.*;

public interface BookingRepository extends JpaRepository<Booking, String> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Booking> findByShowId(String showId);

    List<Booking> findByShowIdIn(Collection<String> showIds);

    long countByShowId(String showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findByTicketCode(String ticketCode);

    @Query(
            "select b from Booking b where b.showId=:showId and b.mobile=:mobile and b.status in"
                    + " :statuses")
    List<Booking> activeByMobile(
            @Param("showId") String showId,
            @Param("mobile") String mobile,
            @Param("statuses") Collection<BookingStatus> statuses);

    @Query(
            "select seat from Booking b join b.seatIds seat where b.showId=:showId and b.status in"
                    + " :statuses")
    List<String> findBookedSeatIds(
            @Param("showId") String showId, @Param("statuses") Collection<BookingStatus> statuses);

    @Query(
            "select count(b)>0 from Booking b join b.seatIds seat where b.showId=:showId and seat"
                    + " in :seatIds and b.status in :statuses")
    boolean anySeatBooked(
            @Param("showId") String showId,
            @Param("seatIds") Collection<String> seatIds,
            @Param("statuses") Collection<BookingStatus> statuses);
}
