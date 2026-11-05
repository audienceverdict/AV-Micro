package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.dto.CreateBookingRequest;
import com.slokam.av.dto.SeatHoldRequest;
import com.slokam.av.dto.TicketVerificationResponse;
import com.slokam.av.entity.Booking;
import com.slokam.av.entity.BookingStatus;
import com.slokam.av.entity.ConfirmationStatus;
import com.slokam.av.entity.Payment;
import com.slokam.av.entity.PaymentStatus;
import com.slokam.av.entity.SeatHold;
import com.slokam.av.entity.SeatHoldStatus;
import com.slokam.av.entity.ShowStatus;
import com.slokam.av.entity.TicketScanAudit;
import com.slokam.av.entity.TicketType;
import com.slokam.av.entity.User;
import com.slokam.av.entity.WaitingListStatus;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.BookingRepository;
import com.slokam.av.repository.MovieRepository;
import com.slokam.av.repository.PaymentRepository;
import com.slokam.av.repository.ScreenRepository;
import com.slokam.av.repository.SeatHoldRepository;
import com.slokam.av.repository.ShowRepository;
import com.slokam.av.repository.TheatreRepository;
import com.slokam.av.repository.TicketScanAuditRepository;
import com.slokam.av.client.IdentityClient;
import com.slokam.av.repository.WaitingListRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final BookingRepository bookings;
    private final ShowRepository shows;
    private final MovieRepository movies;
    private final TheatreRepository theatres;
    private final ScreenRepository screens;
    private final IdentityClient users;
    private final PaymentRepository payments;
    private final NotificationService notifications;
    private final SeatHoldRepository holds;
    private final WaitingListRepository waiting;
    private final ShowSeatService showSeats;
    private final TicketQrService ticketQr;
    private final TicketScanAuditRepository ticketScans;

    @Value("${booking.seat-hold-minutes:5}")
    private long holdMinutes;

    @Value("${booking.waiting-list-offer-minutes:5}")
    private long offerMinutes;

    public BookingService(
            BookingRepository bookings,
            ShowRepository shows,
            MovieRepository movies,
            TheatreRepository theatres,
            ScreenRepository screens,
            IdentityClient users,
            PaymentRepository payments,
            NotificationService notifications,
            SeatHoldRepository holds,
            WaitingListRepository waiting,
            ShowSeatService showSeats,
            TicketQrService ticketQr,
            TicketScanAuditRepository ticketScans) {
        this.bookings = bookings;
        this.shows = shows;
        this.movies = movies;
        this.theatres = theatres;
        this.screens = screens;
        this.users = users;
        this.payments = payments;
        this.notifications = notifications;
        this.holds = holds;
        this.waiting = waiting;
        this.showSeats = showSeats;
        this.ticketQr = ticketQr;
        this.ticketScans = ticketScans;
    }

    @Transactional
    public List<SeatHold> hold(Principal p, SeatHoldRequest r) {
        log.debug("Processing BookingService.hold");
        users.findById(p.getName())
                .orElseThrow(
                        () -> new ApiException(401, "UNAUTHORIZED", "Authentication required"));
        var show =
                shows.lockById(r.showId)
                        .orElseThrow(
                                () -> new ApiException(404, "SHOW_NOT_FOUND", "Show not found"));
        if (show.status != ShowStatus.OPEN)
            throw new ApiException(400, "SHOW_CLOSED", "Show is not open for booking");
        expireHolds();
        if (r.seatIds == null || r.seatIds.isEmpty()) throw new ApiException(400, "INVALID_SEAT", "Select at least one seat");
        var requested = new LinkedHashSet<>(r.seatIds);
        holds.deleteOld(show.id, requested, SeatHoldStatus.EXPIRED);
        if (requested.size() != r.seatIds.size())
            throw new ApiException(400, "DUPLICATE_SEAT", "Seat selected more than once");
        showSeats.validateSelection(show, requested);
        showSeats.rejectOtherHolds(show, requested, p.getName());
        var now = Instant.now();
        var active =
                holds.findByShowIdAndStatusAndExpiresAtAfter(show.id, SeatHoldStatus.ACTIVE, now);
        var booked =
                bookings.findBookedSeatIds(
                        show.id, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
        var held =
                active.stream()
                        .filter(h -> !h.userId.equals(p.getName()))
                        .map(h -> h.seatId)
                        .toList();
        if (requested.stream().anyMatch(s -> booked.contains(s) || held.contains(s)))
            throw new ApiException(
                    409, "SEAT_UNAVAILABLE", "One or more seats are currently unavailable");
        var expiry = now.plusSeconds(Math.max(1, show.holdMinutes) * 60);
        var out =
                new ArrayList<>(
                        active.stream()
                                .filter(
                                        h ->
                                                h.userId.equals(p.getName())
                                                        && requested.contains(h.seatId))
                                .toList());
        for (String seat : requested) {
            if (out.stream().anyMatch(h -> h.seatId.equals(seat))) continue;
            var h = new SeatHold();
            h.showId = show.id;
            h.seatId = seat;
            h.userId = p.getName();
            h.expiresAt = expiry;
            out.add(holds.save(h));
        }
        log.info("Seats held showId={} count={}", show.id, out.size());
        return out;
    }

    public List<SeatHold> activeHolds(Principal p, String showId) {
        log.debug("Processing BookingService.activeHolds");
        expireHolds();
        return holds
                .findByUserIdAndStatusAndExpiresAtAfter(
                        p.getName(), SeatHoldStatus.ACTIVE, Instant.now())
                .stream()
                .filter(h -> h.showId.equals(showId))
                .toList();
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void expireHolds() {
        log.debug("Processing BookingService.expireHolds");
        var now = Instant.now();
        holds.expired(SeatHoldStatus.ACTIVE, now).forEach(h -> h.status = SeatHoldStatus.EXPIRED);
    }

    public List<Booking> mine(Principal p) {
        log.debug("Processing BookingService.mine");
        return bookings.findByUserIdOrderByCreatedAtDesc(p.getName());
    }

    public List<Booking> all() {
        log.debug("Processing BookingService.all");
        return bookings.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Booking get(String id, Principal p) {
        log.debug("Processing BookingService.get");
        var b =
                bookings.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "BOOKING_NOT_FOUND", "Booking not found"));
        if (!b.userId.equals(p.getName()))
            throw new ApiException(403, "FORBIDDEN", "Access denied");
        return b;
    }

    @Transactional
    public byte[] ticketQr(String id, Principal p) {
        var booking = get(id, p);
        if (booking.ticketCode == null || !booking.ticketCode.matches("[A-F0-9]{32}")) {
            booking.ticketCode = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
            bookings.flush();
        }
        return ticketQr.image(booking.ticketCode);
    }

    @Transactional
    public TicketVerificationResponse verifyTicket(String qrPayload, String showId, String staffId) {
        if (qrPayload == null || !qrPayload.matches("AVT1:[A-F0-9]{32}"))
            return rejectScan(qrPayload, null, staffId, "INVALID_QR");
        String ticketCode = qrPayload.substring("AVT1:".length());
        var booking = bookings.findByTicketCode(ticketCode).orElse(null);
        if (booking == null) return rejectScan(qrPayload, null, staffId, "INVALID_QR");
        if (!booking.showId.equals(showId))
            return rejectScan(qrPayload, booking.id, staffId, "WRONG_SHOW");
        if (booking.attended)
            return rejectScan(qrPayload, booking.id, staffId, "ALREADY_USED");
        if (booking.status == BookingStatus.CANCELLED || booking.ticketCount == 0 || booking.seatIds.isEmpty()) {
            return rejectScan(qrPayload, booking.id, staffId, "CANCELLED");
        }
        if (booking.status != BookingStatus.CONFIRMED
                || booking.confirmationStatus == ConfirmationStatus.PENDING) {
            return rejectScan(qrPayload, booking.id, staffId, "BOOKING_NOT_CONFIRMED");
        }
        var payment = payments.findByBookingId(booking.id).orElse(null);
        if (payment == null
                || (payment.status != PaymentStatus.NOT_REQUIRED
                        && payment.status != PaymentStatus.VERIFIED)) {
            return rejectScan(qrPayload, booking.id, staffId, "PAYMENT_NOT_VERIFIED");
        }
        booking.attended = true;
        recordScan(qrPayload, booking.id, staffId, "ADMITTED");
        log.info("Ticket checked in bookingId={} staffId={}", booking.id, staffId);
        return TicketVerificationResponse.admitted(booking);
    }

    private TicketVerificationResponse rejectScan(
            String qrPayload, String bookingId, String staffId, String result) {
        recordScan(qrPayload, bookingId, staffId, result);
        return TicketVerificationResponse.rejected(result);
    }

    private void recordScan(String qrPayload, String bookingId, String staffId, String result) {
        var audit = new TicketScanAudit();
        audit.bookingId = bookingId;
        audit.staffId = staffId;
        audit.credentialHash = sha256(qrPayload == null ? "" : qrPayload);
        audit.result = result;
        ticketScans.save(audit);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Transactional
    public Booking create(Principal p, CreateBookingRequest r) {
        log.debug("Processing BookingService.create");
        User u =
                users.findById(p.getName())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                401, "UNAUTHORIZED", "Authentication required"));
        var show =
                shows.lockById(r.showId)
                        .orElseThrow(
                                () -> new ApiException(404, "SHOW_NOT_FOUND", "Show not found"));
        if (show.status != ShowStatus.OPEN)
            throw new ApiException(400, "SHOW_CLOSED", "Show is not open for booking");
        var now = Instant.now();
        if (show.bookingOpens != null && now.isBefore(show.bookingOpens))
            throw new ApiException(400, "BOOKING_NOT_OPEN", "Booking has not opened");
        if (show.bookingCloses != null && now.isAfter(show.bookingCloses))
            throw new ApiException(400, "BOOKING_CLOSED", "Booking is closed");
        if (r.seatIds == null || r.seatIds.isEmpty()) throw new ApiException(400, "INVALID_SEAT", "Select at least one seat");
        var requested = new ArrayList<>(new LinkedHashSet<>(r.seatIds));
        if (requested.size() != r.seatIds.size())
            throw new ApiException(400, "DUPLICATE_SEAT", "Seat selected more than once");
        var existing =
                bookings
                        .activeByMobile(
                                show.id,
                                u.mobile,
                                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))
                        .stream()
                        .mapToInt(b -> b.ticketCount)
                        .sum();
        if (existing + requested.size() > show.maxTicketsPerMobile)
            throw new ApiException(
                    400, "TICKET_LIMIT", "Ticket limit exceeded for this mobile number");
        var screen =
                screens.findById(show.screenId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "SCREEN_NOT_FOUND", "Screen not found"));
        showSeats.validateSelection(show, requested);
        showSeats.rejectOtherHolds(show, requested, u.id);
        if (bookings.anySeatBooked(
                show.id, requested, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
            throw new ApiException(
                    409, "SEAT_ALREADY_BOOKED", "One or more seats are already booked");
        var movie = movies.findById(show.movieId).orElseThrow();
        var theatre = theatres.findById(show.theatreId).orElseThrow();
        var bookingScreen = screen;
        var b = new Booking();
        b.userId = u.id;
        b.showId = show.id;
        b.seatIds = requested;
        b.ticketCount = requested.size();
        b.totalAmount = show.ticketPrice.multiply(java.math.BigDecimal.valueOf(requested.size()));
        b.status =
                show.ticketType == TicketType.FREE && !show.requireAdminConfirmation
                        ? BookingStatus.CONFIRMED
                        : BookingStatus.PENDING;
        b.confirmationStatus =
                show.requireAdminConfirmation
                        ? ConfirmationStatus.PENDING
                        : ConfirmationStatus.AUTO;
        b.movie = movie.title;
        b.theatre = theatre.name;
        b.screen = bookingScreen.name;
        b.date = show.date.toString();
        b.time = show.startTime.toString();
        b.mobile = u.mobile;
        b.email = u.email;
        b.notification =
                notifications.record(
                        u.id,
                        "BOOKING_CREATED",
                        "Booking " + b.status.name().toLowerCase() + " for " + movie.title);
        bookings.save(b);
        holds
                .findByUserIdAndStatusAndExpiresAtAfter(u.id, SeatHoldStatus.ACTIVE, Instant.now())
                .stream()
                .filter(h -> h.showId.equals(show.id) && requested.contains(h.seatId))
                .forEach(h -> h.status = SeatHoldStatus.CONVERTED);
        var pay = new Payment();
        pay.bookingId = b.id;
        pay.amount = b.totalAmount;
        pay.status =
                show.ticketType == TicketType.FREE
                        ? PaymentStatus.NOT_REQUIRED
                        : PaymentStatus.PENDING;
        payments.save(pay);
        notifications.sendTicket(u, b, movie.posterUrl);
        log.info("Booking state bookingId={} status={} confirmation={}", b.id, b.status, b.confirmationStatus);
        return b;
    }

    @Transactional
    public Booking cancel(String id, Principal p, List<String> seatsToCancel) {
        log.debug("Processing BookingService.cancel");
        var b = get(id, p);
        if (b.status == BookingStatus.CANCELLED) return b;
        if (b.seatIds.size() > 1 && (seatsToCancel == null || seatsToCancel.isEmpty()))
            throw new ApiException(400, "SEATS_REQUIRED", "Choose which seats to cancel");
        var selected =
                seatsToCancel == null || seatsToCancel.isEmpty()
                        ? new ArrayList<>(b.seatIds)
                        : new ArrayList<>(seatsToCancel);
        if (selected.isEmpty() || new HashSet<>(selected).size() != selected.size())
            throw new ApiException(400, "INVALID_SEAT", "Choose each seat only once");
        if (!b.seatIds.containsAll(selected))
            throw new ApiException(
                    400, "INVALID_SEAT", "Selected seat is not part of this booking");
        int previousCount = b.ticketCount;
        var perTicket =
                b.totalAmount.divide(
                        java.math.BigDecimal.valueOf(previousCount),
                        2,
                        java.math.RoundingMode.HALF_UP);
        b.seatIds.removeAll(selected);
        b.ticketCount = b.seatIds.size();
        b.totalAmount =
                b.totalAmount.subtract(
                        perTicket.multiply(java.math.BigDecimal.valueOf(selected.size())));
        if (b.ticketCount == 0) b.status = BookingStatus.CANCELLED;
        b.notification =
                notifications.record(
                        b.userId,
                        "BOOKING_CANCELLED",
                        selected.size() == 1
                                ? "Seat " + selected.get(0) + " cancelled for " + b.movie
                                : "Seats "
                                        + String.join(", ", selected)
                                        + " cancelled for "
                                        + b.movie);
        emailBookingUpdate(b, selected);
        bookings.flush();
        offerNext(b.showId, b.movie);
        log.info("Booking state bookingId={} status={} confirmation={}", b.id, b.status, b.confirmationStatus);
        return b;
    }

    @Transactional
    public Booking adminConfirm(String id) {
        log.debug("Processing BookingService.adminConfirm");
        var b =
                bookings.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "BOOKING_NOT_FOUND", "Booking not found"));
        b.status = BookingStatus.CONFIRMED;
        b.confirmationStatus = ConfirmationStatus.CONFIRMED;
        b.notification =
                notifications.record(
                        b.userId, "BOOKING_CONFIRMED", "Booking confirmed for " + b.movie);
        emailBookingUpdate(b);
        log.info("Booking state bookingId={} status={} confirmation={}", b.id, b.status, b.confirmationStatus);
        return b;
    }

    @Transactional
    public Booking adminCancel(String id, String reason, List<String> seatsToCancel) {
        log.debug("Processing BookingService.adminCancel");
        var b =
                bookings.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "BOOKING_NOT_FOUND", "Booking not found"));
        if (b.status != BookingStatus.CANCELLED) {
            var selected =
                    seatsToCancel == null || seatsToCancel.isEmpty()
                            ? new ArrayList<>(b.seatIds)
                            : new ArrayList<>(seatsToCancel);
            if (!b.seatIds.containsAll(selected))
                throw new ApiException(
                        400, "INVALID_SEAT", "Selected seat is not part of this booking");
            b.seatIds.removeAll(selected);
            b.ticketCount = b.seatIds.size();
            if (b.ticketCount == 0) b.status = BookingStatus.CANCELLED;
            b.cancellationReason = reason.trim();
            b.notification =
                    notifications.record(
                            b.userId,
                            "BOOKING_CANCELLED",
                            "Seats "
                                    + String.join(", ", selected)
                                    + " cancelled for "
                                    + b.movie
                                    + ": "
                                    + b.cancellationReason);
            emailBookingUpdate(b, selected);
            bookings.flush();
            offerNext(b.showId, b.movie);
        }
        log.info("Booking state bookingId={} status={} confirmation={}", b.id, b.status, b.confirmationStatus);
        return b;
    }

    private void offerNext(String showId, String movie) {
        int available = availableSeatCount(showId);
        waiting
                .findByShowIdAndStatusOrderByPositionAscCreatedAtAsc(
                        showId, WaitingListStatus.WAITING)
                .stream()
                .filter(e -> e.requestedSeatsCount <= available)
                .findFirst()
                .ifPresent(
                        e -> {
                            e.status = WaitingListStatus.OFFERED;
                            e.offerExpiresAt = Instant.now().plusSeconds(offerMinutes * 60);
                            String message =
                                    e.requestedSeatsCount
                                            + " seat(s) are available for "
                                            + movie
                                            + ". Complete your booking before the offer expires.";
                            notifications.record(e.userId, "WAITING_LIST_OFFER", message);
                            String poster =
                                    shows.findById(showId)
                                            .flatMap(show -> movies.findById(show.movieId))
                                            .map(item -> item.posterUrl)
                                            .orElse(null);
                            notifications.sendUserMessage(
                                    e.userId,
                                    "WAITING_LIST_OFFER",
                                    "Tickets are available for " + movie,
                                    message,
                                    poster);
                        });
    }

    private void emailBookingUpdate(Booking booking) {
        emailBookingUpdate(booking, List.of());
    }

    private void emailBookingUpdate(Booking booking, List<String> cancelledSeats) {
        users.findById(booking.userId)
                .filter(user -> user.email != null && !user.email.isBlank())
                .ifPresent(
                        user -> {
                            String poster =
                                    shows.findById(booking.showId)
                                            .flatMap(show -> movies.findById(show.movieId))
                                            .map(movie -> movie.posterUrl)
                                            .orElse(null);
                            notifications.sendTicket(user, booking, poster, cancelledSeats);
                        });
    }

    private int availableSeatCount(String showId) {
        var show = shows.findById(showId).orElse(null);
        return show == null ? 0 : (int) showSeats.occupancy(show).availableSeatCount();
    }

    @Transactional
    public Booking markAttended(String id, boolean attended) {
        log.debug("Processing BookingService.markAttended");
        var b =
                bookings.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "BOOKING_NOT_FOUND", "Booking not found"));
        b.attended = attended;
        log.info("Booking state bookingId={} status={} confirmation={}", b.id, b.status, b.confirmationStatus);
        return b;
    }
}
