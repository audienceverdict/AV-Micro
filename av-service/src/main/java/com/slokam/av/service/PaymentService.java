package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.dto.VerifyPaymentRequest;
import com.slokam.av.entity.BookingStatus;
import com.slokam.av.entity.ConfirmationStatus;
import com.slokam.av.entity.Payment;
import com.slokam.av.entity.PaymentStatus;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.BookingRepository;
import com.slokam.av.repository.PaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository payments;
    private final BookingRepository bookings;

    public PaymentService(PaymentRepository payments, BookingRepository bookings) {
        this.payments = payments;
        this.bookings = bookings;
    }

    public Payment getForBooking(String bookingId) {
        log.debug("Processing PaymentService.getForBooking");
        return payments.findByBookingId(bookingId)
                .orElseThrow(() -> new ApiException(404, "PAYMENT_NOT_FOUND", "Payment not found"));
    }

    @Transactional
    public Payment verify(String bookingId, VerifyPaymentRequest r) {
        log.debug("Processing PaymentService.verify");
        var p = getForBooking(bookingId);
        p.provider = r.provider;
        p.providerReference = r.providerReference;
        p.status = PaymentStatus.VERIFIED;
        var b =
                bookings.findById(bookingId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "BOOKING_NOT_FOUND", "Booking not found"));
        if (b.confirmationStatus != ConfirmationStatus.PENDING) b.status = BookingStatus.CONFIRMED;
        log.info("Payment verified bookingId={} status={}", bookingId, p.status);
        return p;
    }
}
