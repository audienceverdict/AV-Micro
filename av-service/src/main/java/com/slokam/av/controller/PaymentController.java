package com.slokam.av.controller;

import com.slokam.av.dto.VerifyPaymentRequest;
import com.slokam.av.entity.Payment;
import com.slokam.av.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Payment")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get payment information associated with a booking", description = "Get payment information associated with a booking. This GET endpoint is public under the current security configuration.")
    @GetMapping("/booking/{bookingId}")
    public Payment get(@PathVariable String bookingId) {
        return service.getForBooking(bookingId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Verify a booking payment using provider and providerReference", description = "Verify a booking payment using provider and providerReference.")
    @PostMapping("/booking/{bookingId}/verify")
    public Payment verify(
            @PathVariable String bookingId, @Valid @RequestBody VerifyPaymentRequest r) {
        return service.verify(bookingId, r);
    }
}
