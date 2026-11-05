package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.dto.WaitingListRequest;
import com.slokam.av.entity.ShowStatus;
import com.slokam.av.entity.WaitingListEntry;
import com.slokam.av.entity.WaitingListStatus;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.ShowRepository;
import com.slokam.av.repository.WaitingListRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Service
public class WaitingListService {
    private static final Logger log = LoggerFactory.getLogger(WaitingListService.class);
    private final WaitingListRepository entries;
    private final ShowRepository shows;
    private final ShowService showService;

    @Value("${booking.waiting-list-offer-minutes:5}")
    private long offerMinutes;

    public WaitingListService(
            WaitingListRepository entries, ShowRepository shows, ShowService showService) {
        this.entries = entries;
        this.shows = shows;
        this.showService = showService;
    }

    public List<WaitingListEntry> mine(Principal p) {
        log.debug("Processing WaitingListService.mine");
        return entries.findAll().stream()
                .filter(e -> e.userId.equals(p.getName()))
                .sorted(
                        Comparator.comparing(
                                (WaitingListEntry e) -> e.createdAt, Comparator.reverseOrder()))
                .toList();
    }

    public List<WaitingListEntry> all() {
        log.debug("Processing WaitingListService.all");
        return entries.findAll().stream()
                .sorted(
                        Comparator.comparing((WaitingListEntry e) -> e.showId)
                                .thenComparingInt(e -> e.position))
                .toList();
    }

    @Transactional
    public WaitingListEntry join(Principal p, WaitingListRequest r) {
        log.debug("Processing WaitingListService.join");
        var show =
                shows.findById(r.showId)
                        .orElseThrow(
                                () -> new ApiException(404, "SHOW_NOT_FOUND", "Show not found"));
        if (show.status != ShowStatus.OPEN)
            throw new ApiException(400, "SHOW_CLOSED", "Show is not open for booking");
        if (r.requestedSeatsCount < 1 || r.requestedSeatsCount > show.maxTicketsPerMobile)
            throw new ApiException(
                    400, "INVALID_TICKET_COUNT", "Requested tickets exceed the screening limit");
        var active =
                entries.findAll().stream()
                        .filter(
                                e ->
                                        e.showId.equals(show.id)
                                                && (e.status == WaitingListStatus.WAITING
                                                        || e.status == WaitingListStatus.OFFERED))
                        .toList();
        if (active.stream().anyMatch(e -> e.userId.equals(p.getName())))
            throw new ApiException(409, "ALREADY_WAITING", "You are already on this waiting list");
        if (availableSeatCount(show.id) == 0) {
            var e = new WaitingListEntry();
            e.showId = show.id;
            e.userId = p.getName();
            e.requestedSeatsCount = r.requestedSeatsCount;
            e.position = active.stream().mapToInt(x -> x.position).max().orElse(0) + 1;
            log.info("Joining waiting list showId={} position={}", show.id, e.position);
            return entries.save(e);
        }
        throw new ApiException(
                409,
                "SEATS_AVAILABLE",
                "Seats are still available. Please book directly instead of joining the waiting"
                        + " list.");
    }

    private long availableSeatCount(String showId) {
        return showService.availableSeatCount(showId);
    }

    @Transactional
    public void cancel(String id, Principal p) {
        log.debug("Processing WaitingListService.cancel");
        var e =
                entries.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404,
                                                "WAITING_LIST_NOT_FOUND",
                                                "Waiting-list entry not found"));
        if (!e.userId.equals(p.getName()))
            throw new ApiException(403, "FORBIDDEN", "Access denied");
        e.status = WaitingListStatus.CANCELLED;
        log.info("Waiting-list entry cancelled entryId={}", id);
    }

    @Transactional
    public WaitingListEntry claim(String id, Principal p) {
        log.debug("Processing WaitingListService.claim");
        var e =
                entries.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404,
                                                "WAITING_LIST_NOT_FOUND",
                                                "Waiting-list entry not found"));
        if (!e.userId.equals(p.getName()))
            throw new ApiException(403, "FORBIDDEN", "Access denied");
        if (e.status != WaitingListStatus.OFFERED
                || e.offerExpiresAt == null
                || e.offerExpiresAt.isBefore(Instant.now()))
            throw new ApiException(409, "OFFER_EXPIRED", "This waiting-list offer has expired");
        e.status = WaitingListStatus.CONVERTED;
        log.info("Waiting-list state entryId={} status={}", e.id, e.status);
        return e;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void expireOffers() {
        log.debug("Processing WaitingListService.expireOffers");
        var now = Instant.now();
        entries.findAll().stream()
                .filter(
                        e ->
                                e.status == WaitingListStatus.OFFERED
                                        && e.offerExpiresAt != null
                                        && e.offerExpiresAt.isBefore(now))
                .forEach(e -> e.status = WaitingListStatus.EXPIRED);
    }
}
