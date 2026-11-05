package com.slokam.av.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrandedEmailTemplateTest {
    @Test
    void notificationPosterReferencesTheAttachmentUsedBySmtp() {
        String poster = "data:image/png;base64,iVBORw0KGgo=";
        var content = BrandedEmailTemplate.message("Reminder", "Your show starts soon", poster);
        assertTrue(content.html().contains("src=\"cid:movie-poster\""));
        org.junit.jupiter.api.Assertions.assertEquals(poster, content.inlinePosterDataUri());
    }

    @Test
    void longBookingDetailsRemainEscapedInResponsiveTableCells() {
        var booking = new com.slokam.av.entity.Booking();
        booking.theatre = "A very long venue name <Main Hall> & Cinema";
        booking.id = "booking-" + "a".repeat(150);
        var content = BrandedEmailTemplate.ticket("User", booking, null,
                java.util.List.of("A1", "A2"), java.util.List.of());
        assertTrue(content.html().contains("class=\"detail-value\""));
        assertTrue(content.html().contains("table-layout:fixed"));
        assertTrue(content.html().contains("overflow-wrap:anywhere"));
        assertTrue(content.html().contains("&lt;Main Hall&gt; &amp; Cinema"));
        assertTrue(content.html().contains(booking.id));
        org.junit.jupiter.api.Assertions.assertFalse(content.html().contains("display:flex"));
    }

    @Test
    void everyEmailTemplateIncludesTheBrandLogo() {
        assertLogo(BrandedEmailTemplate.otp("012345", 5));
        assertLogo(BrandedEmailTemplate.message("Reminder", "Your show starts soon", null));
        var booking = new com.slokam.av.entity.Booking();
        for (var status : com.slokam.av.entity.BookingStatus.values()) {
            booking.status = status;
            assertLogo(BrandedEmailTemplate.ticket("User", booking, null,
                    java.util.List.of("A1"), java.util.List.of()));
        }
        assertLogo(BrandedEmailTemplate.ticket("User", booking, null,
                java.util.List.of("A1"), java.util.List.of("A2")));
    }

    private static void assertLogo(BrandedEmailTemplate.EmailContent content) {
        assertTrue(content.html().contains("src=\"cid:audience-verdict-logo\""));
        assertTrue(content.html().contains("alt=\"Audience Verdict\""));
        assertTrue(content.html().contains("name=\"viewport\""));
        assertTrue(content.html().contains("@media only screen and (max-width:480px)"));
        assertTrue(content.html().contains("class=\"email-content\""));
    }

    @Test
    void otpIsAvailableToPlainTextClientsAndDevelopmentLogs() {
        var content = BrandedEmailTemplate.otp("012345", 5);
        assertTrue(content.plainText().contains("012345"));
        assertTrue(content.plainText().contains("5 minutes"));
        assertTrue(content.html().contains("012345"));
    }
    @Test
    void seatLabelsAppearForEveryBookingStatusAndFullCancellation() {
        var booking = new com.slokam.av.entity.Booking();
        booking.seatIds = java.util.List.of("internal-seat-id");
        for (var status : com.slokam.av.entity.BookingStatus.values()) {
            booking.status = status;
            var content = BrandedEmailTemplate.ticket("User", booking, null,
                    java.util.List.of("A1", "A2"), java.util.List.of());
            assertTrue(content.plainText().contains("Seats: A1, A2"));
            assertTrue(content.html().contains("A1, A2"));
            org.junit.jupiter.api.Assertions.assertFalse(content.plainText().contains("internal-seat-id"));
        }
        booking.status = com.slokam.av.entity.BookingStatus.CANCELLED;
        booking.seatIds = java.util.List.of();
        var cancelled = BrandedEmailTemplate.ticket("User", booking, null,
                java.util.List.of(), java.util.List.of("A1", "A2"));
        assertTrue(cancelled.plainText().contains("Cancelled seats: A1, A2"));
        assertTrue(cancelled.html().contains("Cancelled seats"));
        assertTrue(cancelled.html().contains("A1, A2"));
    }

    @Test
    void ticketEmailEmbedsTheBookingQrCode() {
        var booking = new com.slokam.av.entity.Booking();
        var qr = "data:image/png;base64,aGVsbG8=";

        var content =
                BrandedEmailTemplate.ticket(
                        "User", booking, null, java.util.List.of("A1"), java.util.List.of(), qr);

        assertTrue(content.html().contains("cid:ticket-qr"));
        assertTrue(content.html().contains("Present this QR code at the theater."));
        org.junit.jupiter.api.Assertions.assertEquals(qr, content.inlineQrDataUri());
    }
}
