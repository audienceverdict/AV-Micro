package com.slokam.av.controller;

import com.slokam.av.dto.AdminCancellationRequest;
import com.slokam.av.dto.CancelBookingRequest;
import com.slokam.av.dto.CreateBookingRequest;
import com.slokam.av.dto.SeatHoldRequest;
import com.slokam.av.dto.VerifyTicketRequest;
import com.slokam.av.entity.Booking;
import com.slokam.av.entity.SeatHold;
import com.slokam.av.service.BookingService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Booking")
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService service;
    private final com.slokam.av.service.RoleAccessService roleAccess;

    public BookingController(BookingService service, com.slokam.av.service.RoleAccessService roleAccess) {
        this.service = service;
        this.roleAccess = roleAccess;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List bookings owned by the authenticated account", description = "List bookings owned by the authenticated account.")
    @GetMapping("/me")
    public List<Booking> mine(Principal p) {
        return service.mine(p);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Temporarily hold selected seats for a show", description = "Temporarily hold selected seats for a show. Conflicting or unavailable seats are rejected.")
    @PostMapping("/seat-holds")
    public List<SeatHold> hold(Principal p, @Valid @RequestBody SeatHoldRequest r) {
        return service.hold(p, r);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List the authenticated account active seat holds for the requested show", description = "List the authenticated account active seat holds for the requested show.")
    @GetMapping("/seat-holds")
    public List<SeatHold> activeHolds(Principal p, @RequestParam String showId) {
        return service.activeHolds(p, showId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List all customer bookings", description = "List all customer bookings.")
    @GetMapping("/admin")
    public List<Booking> all() {
        return service.all();
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get a booking accessible to its owner or an administrator", description = "Get a booking accessible to its owner or an administrator.")
    @GetMapping("/{id}")
    public Booking get(@PathVariable String id, Principal p) {
        return service.get(id, p);
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Get the authenticated owner's booking QR code as a PNG image")
    @GetMapping(value = "/{id}/ticket-qr", produces = "image/png")
    public byte[] ticketQr(@PathVariable String id, Principal p) {
        return service.ticketQr(id, p);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create a booking for selected seats and ticket type", description = "Create a booking for selected seats and ticket type. Availability and seat holds are checked.")
    @PostMapping
    public Booking create(Principal p, @Valid @RequestBody CreateBookingRequest r) {
        return service.create(p, r);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Cancel the owner booking", description = "Cancel the owner booking. Omit the body or seatIds to cancel all eligible seats; supply seatIds for partial cancellation.")
    @PostMapping("/{id}/cancel")
    public Booking cancel(
            @PathVariable String id,
            Principal p,
            @RequestBody(required = false) CancelBookingRequest request) {
        return service.cancel(id, p, request == null ? null : request.seatIds());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Confirm a customer booking as an administrator", description = "Confirm a customer booking as an administrator.")
    @PostMapping("/admin/{id}/confirm")
    public Booking confirm(@PathVariable String id) {
        return service.adminConfirm(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Cancel all or selected booking seats with a required administrative reason", description = "Cancel all or selected booking seats with a required administrative reason.")
    @PostMapping("/admin/{id}/cancel")
    public Booking adminCancel(
            @PathVariable String id, @Valid @RequestBody AdminCancellationRequest request) {
        return service.adminCancel(id, request.reason(), request.seatIds());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Set the booking attendance flag", description = "Set the booking attendance flag.")
    @PostMapping("/admin/{id}/attendance")
    public Booking attended(@PathVariable String id, @RequestParam boolean attended) {
        return service.markAttended(id, attended);
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Verify a booking QR code for a show and check in the full booking")
    @PostMapping("/admin/ticket-scan")
    public com.slokam.av.dto.TicketVerificationResponse verifyTicket(
            @Valid @RequestBody VerifyTicketRequest request, Principal principal) {
        roleAccess.requireTicketScanAccess(principal.getName(), request.showId());
        return service.verifyTicket(request.qrPayload(), request.showId(), principal.getName());
    }
}
