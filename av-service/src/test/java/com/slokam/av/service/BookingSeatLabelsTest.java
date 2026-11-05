package com.slokam.av.service;

import com.slokam.av.entity.*;
import com.slokam.av.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BookingSeatLabelsTest {
    @Test
    void usesPinnedLayoutAndPreservesBookingOrder() {
        var shows = mock(ShowRepository.class);
        var layouts = mock(LayoutSeatRepository.class);
        var seats = mock(SeatRepository.class);
        var booking = new Booking();
        booking.showId = "show";
        var show = new Show();
        show.layoutVersionId = "version";
        var a1 = new LayoutSeat(); a1.seatId = "id1"; a1.label = "A1";
        var a2 = new LayoutSeat(); a2.seatId = "id2"; a2.label = "A2";
        var ids = List.of("id2", "id1");
        when(shows.findById("show")).thenReturn(Optional.of(show));
        when(layouts.findByLayoutVersionIdAndIdIn("version", ids)).thenReturn(List.of(a1, a2));
        assertEquals(List.of("A2", "A1"), new BookingSeatLabels(shows, layouts, seats).resolve(booking, ids));
        verifyNoInteractions(seats);
    }

    @Test
    void resolvesLegacySeats() {
        var shows = mock(ShowRepository.class);
        var layouts = mock(LayoutSeatRepository.class);
        var seats = mock(SeatRepository.class);
        var booking = new Booking(); booking.showId = "show";
        when(shows.findById("show")).thenReturn(Optional.of(new Show()));
        var seat = new Seat(); seat.id = "id1"; seat.label = "A1";
        when(seats.findAllById(List.of("id1"))).thenReturn(List.of(seat));
        assertEquals(List.of("A1"), new BookingSeatLabels(shows, layouts, seats).resolve(booking, List.of("id1")));
    }
}
