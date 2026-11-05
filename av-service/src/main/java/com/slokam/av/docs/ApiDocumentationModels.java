package com.slokam.av.docs;

import com.slokam.av.entity.*;
import com.slokam.av.service.ShowSeatService;
import java.util.List;
import java.util.Set;

/** Documentation schemas for the existing map-based response bodies. */
public final class ApiDocumentationModels {
    private ApiDocumentationModels() {}
    public record LayoutDetails(LayoutVersion version, List<LayoutSeat> seats) {}
    public record ShowLayout(LayoutVersion version, List<ShowSeatService.SeatState> seats) {}
    public record ShowAvailability(Show show, Screen screen, ShowLayout layout,
            Set<String> bookedSeatIds, Set<String> reservedSeatIds, Set<String> heldSeatIds,
            long availableSeatCount) {}
}
