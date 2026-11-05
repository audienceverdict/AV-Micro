package com.slokam.av.service;

import com.slokam.av.entity.Booking;
import com.slokam.av.repository.LayoutSeatRepository;
import com.slokam.av.repository.SeatRepository;
import com.slokam.av.repository.ShowRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class BookingSeatLabels {
    private final ShowRepository shows;
    private final LayoutSeatRepository layouts;
    private final SeatRepository seats;

    public BookingSeatLabels(ShowRepository shows, LayoutSeatRepository layouts, SeatRepository seats) {
        this.shows = shows;
        this.layouts = layouts;
        this.seats = seats;
    }

    public List<String> resolve(Booking booking, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        Map<String, String> labels = new HashMap<>();
        var show = shows.findById(booking.showId).orElse(null);
        if (show != null && show.layoutVersionId != null) {
            layouts.findByLayoutVersionIdAndIdIn(show.layoutVersionId, ids)
                    .forEach(seat -> labels.put(seat.bookingSeatId(), seat.label));
        } else {
            seats.findAllById(ids).forEach(seat -> labels.put(seat.id, seat.label));
        }
        return ids.stream().map(id -> labels.getOrDefault(id, id)).toList();
    }
}
