package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.entity.LayoutSeat;
import com.slokam.av.entity.LayoutVersion;
import com.slokam.av.entity.LayoutVersionStatus;
import com.slokam.av.entity.Screen;
import com.slokam.av.entity.Theatre;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.LayoutSeatRepository;
import com.slokam.av.repository.LayoutVersionRepository;
import com.slokam.av.repository.ScreenRepository;
import com.slokam.av.repository.TheatreRepository;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TheatreService {
    private static final Logger log = LoggerFactory.getLogger(TheatreService.class);
    private final TheatreRepository theatres;
    private final ScreenRepository screens;
    private final LayoutVersionRepository versions;
    private final LayoutSeatRepository layoutSeats;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final EntityManager em;
    private final com.slokam.av.repository.ShowRepository shows;

    public TheatreService(
            TheatreRepository theatres,
            ScreenRepository screens,
            LayoutVersionRepository versions,
            LayoutSeatRepository layoutSeats,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            EntityManager em, com.slokam.av.repository.ShowRepository shows) {
        this.theatres = theatres;
        this.screens = screens;
        this.versions = versions;
        this.layoutSeats = layoutSeats;
        this.mapper = mapper;
        this.em = em;
        this.shows = shows;
    }

    public Page<Theatre> list(int page, int size) {
        log.debug("Processing TheatreService.list");
        return theatres.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by("name")));
    }

    public Theatre get(String id) {
        log.debug("Processing TheatreService.get");
        return theatres.findById(id)
                .orElseThrow(() -> new ApiException(404, "THEATRE_NOT_FOUND", "Theatre not found"));
    }

    @Transactional
    public Theatre create(Theatre t) {
        log.debug("Processing TheatreService.create");
        t.id = UUID.randomUUID().toString();
        attachMedia(t);
        log.info("Creating theatre theatreId={}", t.id);
        return theatres.save(t);
    }

    @Transactional
    public Theatre update(String id, Theatre next) {
        log.debug("Processing TheatreService.update");
        var t = get(id);
        t.name = next.name;
        t.address = next.address;
        t.city = next.city;
        t.state = next.state;
        t.contact = next.contact;
        t.status = next.status;
        t.mapUrl = next.mapUrl;
        t.media.clear();
        if (next.media != null) {
            next.media.forEach(
                    m -> {
                        m.id = UUID.randomUUID().toString();
                        m.theatre = t;
                        t.media.add(m);
                    });
        }
        log.info("Theatre updated theatreId={} status={}", t.id, t.status);
        return t;
    }

    @Transactional
    public void delete(String id) {
        log.debug("Processing TheatreService.delete");
        get(id);
        var screenIds =
                em.createNativeQuery("select id from screens where theatre_id=?1")
                        .setParameter(1, id)
                        .getResultList();
        for (Object screenId : screenIds) deleteScreenRows(screenId);
        em.createNativeQuery("delete from venue_media where theatre_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from theatres where id=?1")
                .setParameter(1, id)
                .executeUpdate();
    }

    @Transactional
    public void deleteScreen(String id) {
        log.debug("Processing TheatreService.deleteScreen");
        if (!screens.existsById(id))
            throw new ApiException(404, "SCREEN_NOT_FOUND", "Screen not found");
        deleteScreenRows(id);
        log.info("Screen deleted screenId={}", id);
    }

    private void deleteScreenRows(Object id) {
        lockScreen(id.toString());
        if (!shows.findByScreenId(id.toString()).isEmpty())
            throw new ApiException(409, "SCREEN_HAS_SHOWS", "Screens with shows cannot be deleted; archive the screen instead");
        var versions =
                em.createNativeQuery("select id from layout_versions where screen_id=?1")
                        .setParameter(1, id)
                        .getResultList();
        for (Object v : versions)
            em.createNativeQuery("delete from layout_seats where layout_version_id=?1")
                    .setParameter(1, v)
                    .executeUpdate();
        em.createNativeQuery("delete from layout_versions where screen_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from seats where screen_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from screens where id=?1").setParameter(1, id).executeUpdate();
    }

    public List<Screen> screens(String theatreId) {
        log.debug("Processing TheatreService.screens");
        get(theatreId);
        return screens.findByTheatreId(theatreId).stream().map(this::screenView).toList();
    }

    public List<LayoutVersion> layoutVersions(String screenId) {
        log.debug("Processing TheatreService.layoutVersions");
        return versions.findByScreenIdOrderByVersionNumberDesc(screenId);
    }

    public Map<String, Object> layoutDetails(String screenId, String versionId) {
        log.debug("Processing TheatreService.layoutDetails");
        var v = getLayoutVersion(screenId, versionId);
        return Map.of(
                "version",
                v,
                "seats",
                layoutSeats.findByLayoutVersionIdOrderByRowNumberAscColumnNumberAsc(v.id));
    }

    @Transactional
    public LayoutVersion publishLayoutVersion(String screenId, String versionId) {
        var screen = lockScreen(screenId);
        var draft = editable(screenId, versionId);
        var stored = versionSeats(draft.id);
        validateSeats(stored);
        snapshot(draft, stored);
        for (var previous : versions.findByScreenIdOrderByVersionNumberDesc(screenId)) {
            if (previous.status == LayoutVersionStatus.ACTIVE) {
                previous.status = LayoutVersionStatus.ARCHIVED;
                previous.activeScreenId = null;
            }
        }
        versions.flush(); // Release the unique active-screen slot before assigning it.
        draft.status = LayoutVersionStatus.ACTIVE;
        draft.activeScreenId = screenId;
        draft.publishedAt = java.time.Instant.now();
        screen.activeLayoutVersionId = draft.id;
        screen.layoutVersion = draft.versionNumber;
        screen.rows = stored.stream().mapToInt(x -> x.rowNumber).max().orElseThrow();
        screen.seatsPerRow = stored.stream().mapToInt(x -> x.columnNumber).max().orElseThrow();
        return versions.saveAndFlush(draft);
    }

    @Transactional
    public LayoutVersion createLayoutVersion(String screenId, String sourceVersionId, String name) {
        lockScreen(screenId);
        var source = sourceVersionId == null || sourceVersionId.isBlank()
                ? activeLayout(screenId) : getLayoutVersion(screenId, sourceVersionId);
        var sourceSeats = versionSeats(source.id);
        validateSeats(sourceSeats);
        return newDraft(screenId, name, sourceSeats);
    }

    private LayoutVersion newDraft(String screenId, String name, List<LayoutSeat> sourceSeats) {
        var v = new LayoutVersion();
        v.screenId = screenId;
        v.versionNumber = versions.findByScreenIdOrderByVersionNumberDesc(screenId).stream()
                .mapToInt(x -> x.versionNumber).max().orElse(0) + 1;
        v.name = name == null || name.isBlank() ? "Layout V" + v.versionNumber : name.trim();
        if (v.name.length() > 120) throw new ApiException(400, "INVALID_LAYOUT_NAME", "Layout name is too long");
        v.snapshot = "{}";
        v = versions.saveAndFlush(v);
        final String versionId = v.id;
        var copied = sourceSeats.stream().map(x -> copySeat(x, versionId)).toList();
        layoutSeats.saveAllAndFlush(copied);
        snapshot(v, copied);
        return v;
    }

    @Transactional
    public List<LayoutSeat> saveLayoutSeats(String screenId, String versionId, List<LayoutSeat> input) {
        lockScreen(screenId);
        var v = editable(screenId, versionId);
        validateSeats(input);
        var old = versionSeats(v.id);
        var byId = new HashMap<String, LayoutSeat>();
        old.forEach(x -> byId.put(x.bookingSeatId(), x));
        var byPositionAndLabel = new HashMap<String, LayoutSeat>();
        old.forEach(x -> byPositionAndLabel.put(seatKey(x), x));
        var retained = new HashSet<String>();
        var result = new ArrayList<LayoutSeat>();
        for (var next : input) {
            var previous = next.seatId != null ? byId.get(next.seatId) : byId.get(next.id);
            if (next.seatId != null && previous == null)
                throw new ApiException(400, "INVALID_SEAT_ID", "Seat ID does not belong to this draft; omit IDs for new seats");
            if (previous == null) previous = byPositionAndLabel.get(seatKey(next));
            var saved = previous == null ? new LayoutSeat() : copySeat(previous, v.id);
            if (previous != null) saved.id = previous.id;
            if (saved.seatId == null) saved.seatId = saved.id;
            if (!retained.add(saved.id)) throw new ApiException(400, "DUPLICATE_SEAT", "Seat ID selected more than once");
            saved.layoutVersionId = v.id;
            saved.label = next.label.trim(); saved.rowNumber = next.rowNumber;
            saved.columnNumber = next.columnNumber; saved.category = next.category;
            saved.disabled = next.disabled; saved.color = next.color;
            result.add(saved);
        }
        // Temporarily free unique labels/positions to support swaps; transaction rollback restores them.
        for (var previous : old) {
            previous.label = "~" + previous.id;
            previous.rowNumber = -1;
            previous.columnNumber = old.indexOf(previous) + 1;
        }
        layoutSeats.saveAllAndFlush(old);
        layoutSeats.deleteAll(old.stream().filter(x -> !retained.contains(x.id)).toList());
        layoutSeats.flush();
        var saved = layoutSeats.saveAllAndFlush(result);
        snapshot(v, saved);
        return saved;
    }

    private String seatKey(LayoutSeat s) { return s.label.trim() + ":" + s.rowNumber + ":" + s.columnNumber; }

    public void validateSeats(List<LayoutSeat> input) {
        if (input == null || input.isEmpty()) throw new ApiException(400, "EMPTY_LAYOUT", "A layout must contain at least one seat");
        var labels = new HashSet<String>(); var positions = new HashSet<String>(); var ids = new HashSet<String>();
        for (var seat : input) {
            if (seat == null || seat.label == null || seat.label.isBlank() || seat.label.trim().length() > 80
                    || seat.rowNumber < 1 || seat.columnNumber < 1
                    || !labels.add(seat.label.trim().toUpperCase(Locale.ROOT)) || !positions.add(seat.rowNumber + ":" + seat.columnNumber))
                throw new ApiException(400, "INVALID_SEATS", "Seat labels and positive positions must be unique");
            try { com.slokam.av.entity.SeatCategory.valueOf(seat.category); }
            catch (RuntimeException e) { throw new ApiException(400, "INVALID_CATEGORY", "Unsupported seat category"); }
            if (seat.color != null && !seat.color.matches("#[0-9a-fA-F]{6}"))
                throw new ApiException(400, "INVALID_COLOR", "Seat color must be a six-digit hex color");
            if (seat.seatId != null && (seat.seatId.isBlank() || seat.seatId.length() > 80 || !ids.add(seat.seatId)))
                throw new ApiException(400, "INVALID_SEAT_ID", "Seat IDs must be valid and unique");
        }
    }

    private void snapshot(LayoutVersion v, List<LayoutSeat> stored) {
        try { v.snapshot = mapper.writeValueAsString(Map.of("seats", stored,
                "rows", stored.stream().mapToInt(x -> x.rowNumber).max().orElseThrow(),
                "seatsPerRow", stored.stream().mapToInt(x -> x.columnNumber).max().orElseThrow())); }
        catch (Exception e) { throw new ApiException(500, "LAYOUT_SNAPSHOT_ERROR", "Unable to persist layout snapshot"); }
    }

    /** Snapshots are recovery evidence, not a fallback map served to bookings. */
    public boolean snapshotMatches(LayoutVersion version, List<LayoutSeat> stored) {
        try {
            var root = mapper.readTree(version.snapshot);
            var node = root == null ? null : root.get("seats");
            if (node == null) return root != null && root.isObject(); // Legacy initial snapshots were {}.
            if (!node.isArray()) return false;
            var expected = Arrays.asList(mapper.treeToValue(node, LayoutSeat[].class));
            if (expected.size() != stored.size()) return false;
            return expected.stream().map(this::geometryKey).sorted().toList()
                    .equals(stored.stream().map(this::geometryKey).sorted().toList());
        } catch (Exception e) { return false; }
    }

    private String geometryKey(LayoutSeat s) {
        return s.label + "\u0000" + s.rowNumber + "\u0000" + s.columnNumber + "\u0000"
                + s.category + "\u0000" + s.disabled + "\u0000" + s.color;
    }

    public Screen lockScreen(String screenId) {
        return screens.lockById(screenId).orElseThrow(() -> new ApiException(404, "SCREEN_NOT_FOUND", "Screen not found"));
    }

    private LayoutVersion editable(String screenId, String versionId) {
        var v = getLayoutVersion(screenId, versionId);
        if (v.status != LayoutVersionStatus.DRAFT || shows.existsByLayoutVersionId(v.id))
            throw new ApiException(409, "LAYOUT_LOCKED", "Only drafts without show references can be edited or published");
        return v;
    }

    private LayoutVersion getLayoutVersion(String screenId, String versionId) {
        var v =
                versions.findById(versionId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404,
                                                "LAYOUT_VERSION_NOT_FOUND",
                                                "Layout version not found"));
        if (!v.screenId.equals(screenId))
            throw new ApiException(
                    400, "LAYOUT_SCREEN_MISMATCH", "Layout belongs to another screen");
        return v;
    }

    private LayoutSeat copySeat(LayoutSeat s, String versionId) {
        var copy = new LayoutSeat();
        copy.seatId = s.bookingSeatId();
        copy.layoutVersionId = versionId;
        copy.label = s.label;
        copy.rowNumber = s.rowNumber;
        copy.columnNumber = s.columnNumber;
        copy.category = s.category;
        copy.disabled = s.disabled;
        copy.color = s.color;
        return copy;
    }

    public List<LayoutSeat> versionSeats(String versionId) {
        log.debug("Processing TheatreService.versionSeats");
        return layoutSeats.findByLayoutVersionIdOrderByRowNumberAscColumnNumberAsc(versionId);
    }

    public LayoutVersion activeLayout(String screenId) {
        var screen = screens.findById(screenId).orElseThrow(() -> new ApiException(404, "SCREEN_NOT_FOUND", "Screen not found"));
        var active = versions.findByScreenIdOrderByVersionNumberDesc(screenId).stream()
                .filter(x -> x.status == LayoutVersionStatus.ACTIVE).toList();
        if (active.size() != 1 || (screen.activeLayoutVersionId != null && !screen.activeLayoutVersionId.equals(active.get(0).id)))
            throw new ApiException(409, "LAYOUT_RECONCILIATION_REQUIRED", "Screen must have exactly one consistent active layout");
        var stored = versionSeats(active.get(0).id);
        validateSeats(stored);
        if (!snapshotMatches(active.get(0), stored))
            throw new ApiException(409, "LAYOUT_RECONCILIATION_REQUIRED", "Active layout differs from its persisted snapshot");
        return active.get(0);
    }

    @Transactional
    public Screen createScreen(String theatreId, Screen s) {
        get(theatreId);
        s.id = UUID.randomUUID().toString(); s.theatreId = theatreId;
        s.activeLayoutVersionId = null; s.layoutVersion = 1; s.layoutLocked = false;
        initializeScreenSeats(s);
        attachSeats(s);
        var initialSeats = s.seats == null ? List.<LayoutSeat>of() : s.seats.stream().map(seat -> {
            var x = new LayoutSeat(); x.seatId = seat.id; x.label = seat.label;
            x.rowNumber = seat.rowNumber; x.columnNumber = seat.columnNumber;
            x.category = seat.category; x.disabled = seat.disabled; x.color = seat.color;
            return x;
        }).toList();
        validateSeats(initialSeats);
        var saved = screens.saveAndFlush(s);
        var initial = newDraft(saved.id, "Layout V1", initialSeats);
        publishLayoutVersion(saved.id, initial.id);
        return saved;
    }

    @Transactional
    public Screen updateScreen(String id, Screen next) {
        var screen = lockScreen(id);
        screen.name = next.name; screen.number = next.number; screen.status = next.status;
        // Legacy writes go through the same immutable version workflow.
        if (next.seats != null && !next.seats.isEmpty()) {
            var draft = createLayoutVersion(id, null, null);
            var existingIds = versionSeats(draft.id).stream().map(LayoutSeat::bookingSeatId).collect(java.util.stream.Collectors.toSet());
            var input = next.seats.stream().map(seat -> {
                var x = new LayoutSeat(); x.seatId = existingIds.contains(seat.id) ? seat.id : null; x.label = seat.label;
                x.rowNumber = seat.rowNumber; x.columnNumber = seat.columnNumber;
                x.category = seat.category; x.disabled = seat.disabled; x.color = seat.color;
                return x;
            }).toList();
            saveLayoutSeats(id, draft.id, input);
            publishLayoutVersion(id, draft.id);
        } else {
            throw new ApiException(400, "EMPTY_LAYOUT", "Add at least one seat before saving the layout");
        }
        return screenView(screen);
    }

    private Screen screenView(Screen stored) {
        var view = new Screen(); view.id = stored.id; view.theatreId = stored.theatreId;
        view.name = stored.name; view.number = stored.number; view.rows = stored.rows;
        view.seatsPerRow = stored.seatsPerRow; view.layoutVersion = stored.layoutVersion;
        view.activeLayoutVersionId = stored.activeLayoutVersionId; view.status = stored.status;
        view.createdAt = stored.createdAt; view.updatedAt = stored.updatedAt;
        view.layoutLocked = false;
        // Listing screens must also work for empty or unreconciled legacy layouts.
        // Strict layout validation remains in activeLayout for show creation.
        var active = versions.findByScreenIdOrderByVersionNumberDesc(stored.id).stream()
                .filter(x -> x.status == LayoutVersionStatus.ACTIVE)
                .filter(x -> stored.activeLayoutVersionId == null || stored.activeLayoutVersionId.equals(x.id))
                .findFirst();
        if (active.isEmpty()) {
            view.seats = stored.seats == null ? List.of() : stored.seats;
            return view;
        }
        view.seats = versionSeats(active.get().id).stream().map(x -> {
            var seat = new com.slokam.av.entity.Seat(); seat.id = x.bookingSeatId();
            seat.screen = view; seat.label = x.label; seat.rowNumber = x.rowNumber;
            seat.columnNumber = x.columnNumber; seat.category = x.category;
            seat.color = x.color; seat.disabled = x.disabled; return seat;
        }).toList();
        return view;
    }

    private void attachMedia(Theatre t) {
        if (t.media != null)
            t.media.forEach(
                    m -> {
                        m.id = UUID.randomUUID().toString();
                        m.theatre = t;
                    });
    }

    private void initializeScreenSeats(Screen screen) {
        if (screen.seats != null && !screen.seats.isEmpty()) return;
        if (screen.rows < 1 || screen.seatsPerRow < 1) {
            throw new ApiException(400, "EMPTY_LAYOUT",
                    "Provide at least one seat or positive rows and seatsPerRow to initialize the layout");
        }
        long capacity = (long) screen.rows * screen.seatsPerRow;
        if (capacity > 10_000) {
            throw new ApiException(400, "INVALID_LAYOUT_DIMENSIONS",
                    "An automatically generated layout cannot exceed 10000 seats");
        }
        screen.seats = new ArrayList<>((int) capacity);
        for (int row = 1; row <= screen.rows; row++) {
            String label = rowLabel(row);
            for (int column = 1; column <= screen.seatsPerRow; column++) {
                var seat = new com.slokam.av.entity.Seat();
                seat.label = label + column;
                seat.rowNumber = row;
                seat.columnNumber = column;
                screen.seats.add(seat);
            }
        }
    }

    private String rowLabel(int row) {
        var label = new StringBuilder();
        for (int value = row; value > 0; value = (value - 1) / 26) {
            label.append((char) ('A' + (value - 1) % 26));
        }
        return label.reverse().toString();
    }

    private void attachSeats(Screen s) {
        if (s.seats != null)
            s.seats.forEach(
                    seat -> {
                        seat.id =
                                seat.id == null || seat.id.isBlank()
                                        ? UUID.randomUUID().toString()
                                        : seat.id;
                        seat.screen = s;
                    });
    }
}
