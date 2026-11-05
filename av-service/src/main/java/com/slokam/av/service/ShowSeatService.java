package com.slokam.av.service;

import com.slokam.av.entity.*;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Reads immutable layout geometry and overlays occupancy without changing seat entities. */
@Service
public class ShowSeatService {
    private final LayoutVersionRepository versions;
    private final LayoutSeatRepository seats;
    private final BookingRepository bookings;
    private final SeatHoldRepository holds;
    private final TheatreService layouts;

    public ShowSeatService(LayoutVersionRepository versions, LayoutSeatRepository seats,
                           BookingRepository bookings, SeatHoldRepository holds, TheatreService layouts) {
        this.versions = versions; this.seats = seats; this.bookings = bookings; this.holds = holds; this.layouts = layouts;
    }

    public List<LayoutSeat> layout(Show show) {
        if (show.layoutVersionId == null) throw inconsistent();
        var version = versions.findById(show.layoutVersionId).orElseThrow(this::inconsistent);
        if (!version.screenId.equals(show.screenId) || version.status == LayoutVersionStatus.DRAFT) throw inconsistent();
        var result = seats.findByLayoutVersionIdOrderByRowNumberAscColumnNumberAsc(version.id);
        try { layouts.validateSeats(result); } catch (ApiException e) { throw inconsistent(); }
        if (!layouts.snapshotMatches(version, result)) throw inconsistent();
        return result;
    }

    public List<LayoutSeat> validateSelection(Show show, Collection<String> requested) {
        if (requested == null || requested.isEmpty() || requested.stream().anyMatch(Objects::isNull))
            throw new ApiException(400, "INVALID_SEAT", "Select at least one valid seat");
        var selected = layout(show).stream().filter(x -> requested.contains(x.bookingSeatId())).toList();
        if (selected.size() != requested.size() || selected.stream().anyMatch(x -> x.disabled))
            throw new ApiException(400, "INVALID_SEAT", "Seats must be enabled and belong to this show's layout");
        return selected;
    }

    public record SeatState(String id, String layoutVersionId, String label, int rowNumber,
                            int columnNumber, String category, String color, boolean disabled,
                            String state, boolean clickable, Instant holdExpiresAt) {}
    public record Occupancy(List<SeatState> seats, Set<String> unavailableSeatIds,
                            Set<String> reservedSeatIds, Set<String> heldSeatIds, long availableSeatCount) {}

    public Occupancy occupancy(Show show) {
        var layout = layout(show);
        var reserved = new HashSet<>(bookings.findBookedSeatIds(show.id,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)));
        var expiryById = new HashMap<String, Instant>();
        holds.findByShowIdAndStatusAndExpiresAtAfter(show.id, SeatHoldStatus.ACTIVE, Instant.now())
                .forEach(h -> expiryById.merge(h.seatId, h.expiresAt, (a,b) -> a.isAfter(b) ? a : b));
        var layoutIds = new HashSet<>(layout.stream().map(LayoutSeat::bookingSeatId).toList());
        // Never hide an orphaned booking and accidentally offer its physical seat again.
        if (!layoutIds.containsAll(reserved) || !layoutIds.containsAll(expiryById.keySet())) throw inconsistent();
        var unavailable = new LinkedHashSet<String>();
        var states = layout.stream().map(s -> {
            var id = s.bookingSeatId();
            String state = s.disabled ? "DISABLED" : reserved.contains(id) ? "RESERVED"
                    : expiryById.containsKey(id) ? "HELD" : "AVAILABLE";
            boolean clickable = state.equals("AVAILABLE");
            if (!clickable) unavailable.add(id);
            return new SeatState(id, show.layoutVersionId, s.label, s.rowNumber, s.columnNumber,
                    s.category, s.color, !clickable, state, clickable,
                    state.equals("HELD") ? expiryById.get(id) : null);
        }).toList();
        return new Occupancy(states, unavailable, reserved, expiryById.keySet(),
                states.stream().filter(SeatState::clickable).count());
    }

    public void rejectOtherHolds(Show show, Collection<String> requested, String userId) {
        // Also checks orphaned reservations before accepting new occupancy.
        occupancy(show);
        if (holds.findByShowIdAndStatusAndExpiresAtAfter(show.id, SeatHoldStatus.ACTIVE, Instant.now())
                .stream().anyMatch(h -> requested.contains(h.seatId) && !h.userId.equals(userId)))
            throw new ApiException(409, "SEAT_UNAVAILABLE", "One or more seats are held by another user");
    }

    private ApiException inconsistent() {
        return new ApiException(409, "LAYOUT_RECONCILIATION_REQUIRED",
                "Show layout is missing or inconsistent; explicit reconciliation is required");
    }
}
