package com.slokam.av.controller;

import com.slokam.av.dto.TicketScanHistoryResponse;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.TicketScanHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ticket-scans")
public class TicketScanHistoryController {
    private final TicketScanHistoryRepository history;

    public TicketScanHistoryController(TicketScanHistoryRepository history) { this.history = history; }

    @io.swagger.v3.oas.annotations.Operation(summary = "List ticket scan history with optional filters",
            description = "Admin-only. Combine verifierId (scanner), customerId (ticket owner), showId and theatreId filters. Newest first; page is zero-based and size is 1–100. Includes rejected scans. Unidentified tickets have null booking context. Show and theatre refer to the ticket's booked show, not the attempted scan location.")
    @GetMapping
    @Transactional(readOnly = true)
    public Page<TicketScanHistoryResponse> list(
            @RequestParam(required = false) String verifierId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String showId,
            @RequestParam(required = false) String theatreId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ApiException(400, "INVALID_PAGINATION", "page must be non-negative and size must be between 1 and 100");
        return history.history(verifierId, customerId, showId, theatreId, PageRequest.of(page, size));
    }
}
