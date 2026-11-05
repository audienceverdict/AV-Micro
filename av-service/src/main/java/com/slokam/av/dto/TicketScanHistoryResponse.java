package com.slokam.av.dto;

import java.time.Instant;

public record TicketScanHistoryResponse(
        String id, String bookingId, String verifierId, String customerId,
        String showId, String theatreId, String result, Instant scannedAt) {}
