package com.slokam.av.repository;

import com.slokam.av.entity.TicketScanAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketScanAuditRepository extends JpaRepository<TicketScanAudit, String> {
    List<TicketScanAudit> findByBookingIdOrderByScannedAtDesc(String bookingId);
}
