package com.slokam.av.service;

import com.slokam.av.dto.CampaignRequest;
import com.slokam.av.dto.CampaignResult;
import com.slokam.av.entity.Booking;
import com.slokam.av.entity.BookingStatus;
import com.slokam.av.entity.Notification;
import com.slokam.av.entity.User;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.BookingRepository;
import com.slokam.av.repository.MovieRepository;
import com.slokam.av.repository.NotificationRepository;
import com.slokam.av.repository.ShowRepository;
import com.slokam.av.client.IdentityClient;
import com.slokam.av.util.BrandedEmailTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final IdentityClient users;
    private final BookingRepository bookings;
    private final ShowRepository shows;
    private final MovieRepository movies;
    private final EmailDeliveryService email;
    private final BookingSeatLabels seatLabels;
    private final TicketQrService ticketQr;

    public NotificationService(
            NotificationRepository notifications,
            IdentityClient users,
            BookingRepository bookings,
            ShowRepository shows,
            MovieRepository movies,
            EmailDeliveryService email, BookingSeatLabels seatLabels, TicketQrService ticketQr) {
        this.notifications = notifications;
        this.users = users;
        this.bookings = bookings;
        this.shows = shows;
        this.movies = movies;
        this.email = email;
        this.seatLabels = seatLabels;
        this.ticketQr = ticketQr;
    }

    public String record(String userId, String type, String message) {
        log.debug("Processing NotificationService.record");
        var n = new Notification();
        n.userId = userId;
        n.type = type;
        n.message = message;
        notifications.save(n);
        return message;
    }

    public void sendTicket(User user, Booking booking, String posterUrl) {
        log.debug("Processing NotificationService.sendTicket");
        sendTicket(user, booking, posterUrl, List.of());
    }

    public void sendTicket(User user, Booking booking, String posterUrl, List<String> cancelledIds) {
        String subject = !cancelledIds.isEmpty() ? "Booking cancellation for " + booking.movie
                : booking.status == BookingStatus.CONFIRMED ? "Your ticket for " + booking.movie + " is confirmed"
                : "Booking update for " + booking.movie;
        var content = BrandedEmailTemplate.ticket(user.name, booking, posterUrl,
                seatLabels.resolve(booking, booking.seatIds), seatLabels.resolve(booking, cancelledIds),
                ticketQr.imageDataUri(booking.ticketCode));
        String userId = user.id;
        String type = "BOOKING_" + booking.status.name();
        email.sendHtml(user.email, subject, content.plainText(), content.html(),
                content.inlinePosterDataUri(), content.inlineQrDataUri(),
                () -> record(userId, type, "Email sent: " + subject),
                () -> record(userId, "BOOKING_EMAIL_DELIVERY_PENDING",
                        "Ticket details are saved in My Bookings. Email delivery can be retried."));
    }

    public void sendUserMessage(
            String userId, String type, String subject, String body, String posterUrl) {
        log.debug("Processing NotificationService.sendUserMessage");
        users.findById(userId)
                .filter(user -> !blank(user.email))
                .ifPresent(
                        user -> {
                            try {
                                dispatch(user, type, subject, body, posterUrl);
                            } catch (RuntimeException e) {
                                log.warn(
                                        "Email delivery failed for user {}: {}",
                                        userId,
                                        e.getClass().getSimpleName());
                                record(
                                        userId,
                                        type + "_EMAIL_PENDING",
                                        subject + " Email delivery can be retried.");
                            }
                        });
    }

    @Transactional
    public CampaignResult send(CampaignRequest request) {
        log.debug("Processing NotificationService.send");
        if (request.target() != CampaignRequest.Target.ALL_USERS
                && request.target() != CampaignRequest.Target.SHOW_BOOKERS
                && (request.movieId() == null || request.movieId().isBlank()))
            throw new ApiException(400, "MOVIE_REQUIRED", "Choose a movie audience.");
        if (request.target() == CampaignRequest.Target.SHOW_BOOKERS
                && (request.showId() == null || !shows.existsById(request.showId())))
            throw new ApiException(404, "SHOW_NOT_FOUND", "Choose an existing show.");
        var showIds =
                request.target() == CampaignRequest.Target.SHOW_BOOKERS
                        ? List.of(request.showId())
                        : request.target() == CampaignRequest.Target.MOVIE_BOOKERS
                                ? shows.findAll().stream()
                                        .filter(s -> s.movieId.equals(request.movieId()))
                                        .map(s -> s.id)
                                        .toList()
                                : List.<String>of();
        var booked =
                request.target() == CampaignRequest.Target.ALL_USERS
                        ? List.<Booking>of()
                        : request.target() == CampaignRequest.Target.SHOW_BOOKERS
                                ? bookings.findByShowId(request.showId())
                                : showIds.isEmpty()
                                        ? List.<Booking>of()
                                        : bookings.findByShowIdIn(showIds);
        Set<String> ids =
                booked.stream()
                        .filter(b -> b.status != BookingStatus.CANCELLED)
                        .map(b -> b.userId)
                        .collect(Collectors.toSet());
        var recipients =
                users.findByEnabledTrue().stream()
                        .filter(
                                u ->
                                        request.target() == CampaignRequest.Target.ALL_USERS
                                                || ids.contains(u.id))
                        .filter(
                                u ->
                                        request.status() == null
                                                || request.status().isBlank()
                                                || booked.stream()
                                                        .anyMatch(
                                                                b ->
                                                                        b.userId.equals(u.id)
                                                                                && b.status
                                                                                        .name()
                                                                                        .equalsIgnoreCase(
                                                                                                request
                                                                                                        .status())))
                        .toList();
        Comparator<User> comparator =
                switch (request.sortBy() == null
                        ? "name"
                        : request.sortBy().toLowerCase(Locale.ROOT)) {
                    case "email" ->
                            Comparator.comparing(
                                    u -> Objects.toString(u.email, ""),
                                    String.CASE_INSENSITIVE_ORDER);
                    case "joined" -> Comparator.comparing(u -> u.createdAt);
                    case "mobile" -> Comparator.comparing(u -> u.mobile);
                    default -> Comparator.comparing(u -> u.name, String.CASE_INSENSITIVE_ORDER);
                };
        var sorted = new ArrayList<>(recipients);
        sorted.sort(comparator);
        if ("desc".equalsIgnoreCase(request.sortDirection())) Collections.reverse(sorted);
        int sent = 0, skipped = 0;
        for (User user : sorted) {
            if (blank(user.email)) {
                skipped++;
                continue;
            }
            String body = personalize(request.message(), user, booked);
            var booking =
                    booked.stream().filter(b -> b.userId.equals(user.id)).findFirst().orElse(null);
            String poster = booking == null ? null : posterFor(booking);
            dispatch(user, "ADMIN_CAMPAIGN", request.subject(), body, poster);
            sent++;
        }
        log.info("Email campaign processed recipients={} sent={} skipped={}", sorted.size(), sent, skipped);
        return new CampaignResult(sorted.size(), sent, skipped, "EMAIL");
    }

    @Transactional
    public CampaignResult sendShowReminder(String showId) {
        log.debug("Processing NotificationService.sendShowReminder");
        shows.findById(showId)
                .orElseThrow(() -> new ApiException(404, "SHOW_NOT_FOUND", "Show not found"));
        var rows =
                bookings.findByShowId(showId).stream()
                        .filter(b -> b.status != BookingStatus.CANCELLED)
                        .toList();
        var request =
                new CampaignRequest(
                        CampaignRequest.Target.SHOW_BOOKERS,
                        "Reminder: "
                                + rows.stream().findFirst().map(b -> b.movie).orElse("your movie"),
                        "Hi {{name}}, a reminder for {{movie}} on {{show_date}} at {{show_time}}."
                                + " Booking {{booking_id}}.",
                        showId,
                        null,
                        null,
                        "name",
                        "asc");
        return send(request);
    }

    private void dispatch(User user, String type, String subject, String body, String posterUrl) {
        var content = BrandedEmailTemplate.message(subject, body, posterUrl);
        email.sendHtml(
                user.email,
                subject,
                content.plainText(),
                content.html(),
                content.inlinePosterDataUri(),
                () -> record(user.id, type, truncate(subject + ": " + body + " [delivery:email]")),
                () -> record(user.id, type + "_EMAIL_PENDING", "Email delivery can be retried."));
    }

    private String posterFor(Booking booking) {
        return shows.findById(booking.showId)
                .flatMap(show -> movies.findById(show.movieId))
                .map(movie -> movie.posterUrl)
                .orElse(null);
    }

    private String personalize(String template, User user, List<Booking> rows) {
        Booking booking =
                rows.stream().filter(b -> b.userId.equals(user.id)).findFirst().orElse(null);
        return template.replace("{{name}}", safeName(user.name))
                .replace("{{email}}", Objects.toString(user.email, ""))
                .replace("{{mobile}}", Objects.toString(user.mobile, ""))
                .replace("{{movie}}", booking == null ? "" : booking.movie)
                .replace("{{show_date}}", booking == null ? "" : booking.date)
                .replace("{{show_time}}", booking == null ? "" : booking.time)
                .replace("{{booking_id}}", booking == null ? "" : booking.id)
                .replace("{{seats}}", booking == null ? "" : String.join(", ", seatLabels.resolve(booking, booking.seatIds)))
                + (booking == null ? "" : "\nSeats: " + String.join(", ", seatLabels.resolve(booking, booking.seatIds)));
    }

    private String safeName(String name) {
        return name == null || name.isBlank() ? "there" : name.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value) {
        return value.length() > 490 ? value.substring(0, 487) + "..." : value;
    }
}
