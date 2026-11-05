package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.entity.ActiveStatus;
import com.slokam.av.entity.MovieStatus;
import com.slokam.av.entity.Show;
import com.slokam.av.entity.ShowStatus;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.BookingRepository;
import com.slokam.av.repository.MovieRepository;
import com.slokam.av.repository.ScreenRepository;
import com.slokam.av.repository.ShowRepository;
import com.slokam.av.repository.TheatreRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class ShowService {
    private static final Logger log = LoggerFactory.getLogger(ShowService.class);
    private final ShowRepository shows;
    private final MovieRepository movies;
    private final TheatreRepository theatres;
    private final ScreenRepository screens;
    private final BookingRepository bookings;
    private final TheatreService layouts;
    private final ShowSeatService showSeats;

    public ShowService(
            ShowRepository shows,
            MovieRepository movies,
            TheatreRepository theatres,
            ScreenRepository screens,
            BookingRepository bookings,
            TheatreService layouts, ShowSeatService showSeats) {
        this.shows = shows;
        this.movies = movies;
        this.theatres = theatres;
        this.screens = screens;
        this.bookings = bookings;
        this.layouts = layouts;
        this.showSeats = showSeats;
    }

    public List<Show> list(String movieId, String theatreId, LocalDate date) {
        log.debug("Processing ShowService.list");
        var result =
                movieId != null
                        ? shows.findByMovieIdAndDateGreaterThanEqual(
                                movieId, date == null ? LocalDate.now() : date)
                        : theatreId != null && date != null
                                ? shows.findByTheatreIdAndDate(theatreId, date)
                                : shows.findAll();
        return result.stream().filter(show -> show.status != ShowStatus.CANCELLED).toList();
    }

    public Show get(String id) {
        log.debug("Processing ShowService.get");
        return shows.findById(id)
                .orElseThrow(() -> new ApiException(404, "SHOW_NOT_FOUND", "Show not found"));
    }

    public Map<String, Object> availability(String id) {
        log.debug("Processing ShowService.availability");
        var show = get(id);
        var screen =
                screens.findById(show.screenId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "SCREEN_NOT_FOUND", "Screen not found"));
        var occupancy = showSeats.occupancy(show);
        var version = layouts.layoutDetails(show.screenId, show.layoutVersionId);
        var layout = new LinkedHashMap<String, Object>(version);
        layout.put("seats", occupancy.seats());
        // Compatibility: screen.seats must also describe this show, never the newer active map.
        var view = new com.slokam.av.entity.Screen();
        view.id = screen.id; view.theatreId = screen.theatreId; view.name = screen.name;
        view.number = screen.number; view.status = screen.status;
        view.createdAt = screen.createdAt; view.updatedAt = screen.updatedAt;
        view.layoutVersion = show.layoutVersion; view.activeLayoutVersionId = screen.activeLayoutVersionId;
        view.rows = occupancy.seats().stream().mapToInt(ShowSeatService.SeatState::rowNumber).max().orElse(0);
        view.seatsPerRow = occupancy.seats().stream().mapToInt(ShowSeatService.SeatState::columnNumber).max().orElse(0);
        view.seats = occupancy.seats().stream().map(x -> {
            var seat = new com.slokam.av.entity.Seat(); seat.id = x.id(); seat.screen = view;
            seat.label = x.label(); seat.rowNumber = x.rowNumber(); seat.columnNumber = x.columnNumber();
            seat.category = x.category(); seat.color = x.color(); seat.disabled = x.disabled(); return seat;
        }).toList();
        return Map.of("show", show, "screen", view, "layout", layout,
                "bookedSeatIds", occupancy.unavailableSeatIds(), "reservedSeatIds", occupancy.reservedSeatIds(),
                "heldSeatIds", occupancy.heldSeatIds(), "availableSeatCount", occupancy.availableSeatCount());
    }

    public long availableSeatCount(String id) { return showSeats.occupancy(get(id)).availableSeatCount(); }

    @Transactional
    public Show create(Show s) {
        log.debug("Processing ShowService.create");
        layouts.lockScreen(s.screenId);
        validateRelations(s);
        validateOverlap(null, s);
        var active = layouts.activeLayout(s.screenId);
        s.layoutVersionId = active.id;
        s.layoutVersion = active.versionNumber;
        s.id = UUID.randomUUID().toString();
        log.info("Creating show showId={}", s.id);
        return shows.save(s);
    }

    @Transactional
    public Show update(String id, Show next) {
        log.debug("Processing ShowService.update");
        var s = get(id);
        next.id = id;
        validateRelations(next);
        validateOverlap(id, next);
        if (!Objects.equals(s.screenId, next.screenId))
            throw new ApiException(
                    400,
                    "SHOW_SCREEN_IMMUTABLE",
                    "Create a new show to change screens or layout versions");
        s.movieId = next.movieId;
        s.theatreId = next.theatreId;
        s.date = next.date;
        s.seatPrices = next.seatPrices;
        s.startTime = next.startTime;
        s.endTime = next.endTime;
        s.ticketType = next.ticketType;
        s.ticketPrice = next.ticketPrice;
        s.maxTicketsPerMobile = next.maxTicketsPerMobile;
        s.requireAdminConfirmation = next.requireAdminConfirmation;
        s.bookingOpens = next.bookingOpens;
        s.bookingCloses = next.bookingCloses;
        s.status = next.status;
        log.info("Show updated showId={} status={}", id, s.status);
        return s;
    }

    @Transactional
    public void cancel(String id) {
        log.debug("Processing ShowService.cancel");
        var show = get(id);
        if (bookings.countByShowId(id) == 0) {
            shows.delete(show);
            log.info("Show deleted showId={}", id);
        } else {
            show.status = ShowStatus.CANCELLED;
            log.info("Show cancelled showId={}", id);
        }
    }

    private void validateRelations(Show s) {
        movies.findById(s.movieId)
                .filter(m -> m.status != MovieStatus.ENDED)
                .orElseThrow(() -> new ApiException(400, "INVALID_MOVIE", "Movie is unavailable"));
        theatres.findById(s.theatreId)
                .filter(t -> t.status == ActiveStatus.ACTIVE)
                .orElseThrow(
                        () -> new ApiException(400, "INVALID_THEATRE", "Theatre is unavailable"));
        screens.findById(s.screenId)
                .filter(sc -> sc.status == ActiveStatus.ACTIVE && sc.theatreId.equals(s.theatreId))
                .orElseThrow(
                        () -> new ApiException(400, "INVALID_SCREEN", "Screen is unavailable"));
    }

    private void validateOverlap(String id, Show s) {
        if (!s.startTime.isBefore(s.endTime))
            throw new ApiException(400, "INVALID_TIME", "Show start time must be before end time");
        var overlaps =
                shows
                        .overlapping(
                                s.screenId, s.date, s.startTime, s.endTime, ShowStatus.CANCELLED)
                        .stream()
                        .anyMatch(o -> !o.id.equals(id));
        if (overlaps)
            throw new ApiException(
                    409, "SHOW_OVERLAP", "Screen already has a show in this time range");
    }
}
