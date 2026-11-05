package com.slokam.av.repository;

import com.slokam.av.dto.TicketScanHistoryResponse;
import com.slokam.av.entity.TicketScanAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only history queries; the existing scan writer remains unchanged. */
public interface TicketScanHistoryRepository extends Repository<TicketScanAudit, String> {
    @Query(value = """
            select new com.slokam.av.dto.TicketScanHistoryResponse(
                a.id, a.bookingId, a.staffId, b.userId, b.showId, s.theatreId, a.result, a.scannedAt)
            from TicketScanAudit a
            left join Booking b on b.id = a.bookingId
            left join Show s on s.id = b.showId
            where (:verifierId is null or a.staffId = :verifierId)
              and (:customerId is null or b.userId = :customerId)
              and (:showId is null or b.showId = :showId)
              and (:theatreId is null or s.theatreId = :theatreId)
            order by a.scannedAt desc, a.id desc
            """, countQuery = """
            select count(a) from TicketScanAudit a
            left join Booking b on b.id = a.bookingId
            left join Show s on s.id = b.showId
            where (:verifierId is null or a.staffId = :verifierId)
              and (:customerId is null or b.userId = :customerId)
              and (:showId is null or b.showId = :showId)
              and (:theatreId is null or s.theatreId = :theatreId)
            """)
    Page<TicketScanHistoryResponse> history(
            @Param("verifierId") String verifierId, @Param("customerId") String customerId,
            @Param("showId") String showId, @Param("theatreId") String theatreId, Pageable pageable);
}
