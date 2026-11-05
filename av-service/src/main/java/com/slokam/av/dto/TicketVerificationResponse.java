package com.slokam.av.dto;

import com.slokam.av.entity.Booking;
import java.util.List;

public record TicketVerificationResponse(
        boolean valid,
        String result,
        String bookingId,
        String movie,
        String theatre,
        String screen,
        String date,
        String time,
        List<String> seatIds,
        int ticketCount) {

    public static TicketVerificationResponse rejected(String result) {
        return new TicketVerificationResponse(
                false, result, null, null, null, null, null, null, List.of(), 0);
    }

    public static TicketVerificationResponse admitted(Booking booking) {
        return new TicketVerificationResponse(
                true,
                "ADMITTED",
                booking.id,
                booking.movie,
                booking.theatre,
                booking.screen,
                booking.date,
                booking.time,
                List.copyOf(booking.seatIds),
                booking.ticketCount);
    }
}
