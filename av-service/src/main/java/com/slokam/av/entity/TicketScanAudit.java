package com.slokam.av.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ticket_scan_audits",
        indexes = @Index(name = "idx_ticket_scan_audits_booking", columnList = "booking_id, scanned_at"))
public class TicketScanAudit {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(length = 36)
    public String bookingId;

    @Column(nullable = false, length = 36)
    public String staffId;

    @Column(nullable = false, length = 64)
    public String credentialHash;

    @Column(nullable = false, length = 40)
    public String result;

    @Column(nullable = false)
    public Instant scannedAt = Instant.now();
}
