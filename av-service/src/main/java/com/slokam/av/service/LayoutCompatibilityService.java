package com.slokam.av.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.av.entity.*;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Explicit recovery only: no startup backfill, no recovery on read, no deleted booking data. */
@Service
public class LayoutCompatibilityService {
    private final ScreenRepository screens;
    private final ShowRepository shows;
    private final LayoutVersionRepository versions;
    private final LayoutSeatRepository seats;
    private final BookingRepository bookings;
    private final SeatHoldRepository holds;
    private final TheatreService layouts;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public LayoutCompatibilityService(ScreenRepository screens, ShowRepository shows,
            LayoutVersionRepository versions, LayoutSeatRepository seats, BookingRepository bookings,
            SeatHoldRepository holds, TheatreService layouts, ObjectMapper mapper, EntityManager em) {
        this.screens=screens; this.shows=shows; this.versions=versions; this.seats=seats;
        this.bookings=bookings; this.holds=holds; this.layouts=layouts; this.mapper=mapper; this.em=em;
    }

    public record Assessment(String showId, String previousVersionId, boolean consistent,
                             boolean repairable, String source, String issue) {}
    private record Plan(Assessment assessment, List<LayoutSeat> seats) {}

    @Transactional(readOnly = true)
    public List<Assessment> assess(String screenId, boolean adoptCurrentLegacyLayout) {
        return plans(screenId, adoptCurrentLegacyLayout).stream().map(Plan::assessment).toList();
    }

    @Transactional
    public List<Assessment> reconcile(String screenId, boolean adoptCurrentLegacyLayout) {
        var screen = layouts.lockScreen(screenId);
        // Occupancy writes lock each show too. Lock in a stable order before planning.
        shows.findByScreenId(screenId).stream().sorted(Comparator.comparing(x -> x.id))
                .forEach(x -> shows.lockById(x.id).orElseThrow());
        var plans = plans(screenId, adoptCurrentLegacyLayout);
        if (plans.stream().anyMatch(x -> !x.assessment.repairable))
            throw new ApiException(409, "LAYOUT_RECOVERY_AMBIGUOUS", "Inspect layout compatibility assessment; unresolved data will not be overwritten");
        for (var plan : plans) {
            var assessment = plan.assessment;
            if (assessment.consistent) continue;
            var v = recovered(screenId, plan.seats, assessment.showId == null);
            var audit = new LayoutRecovery(); audit.screenId=screenId;
            audit.showId=assessment.showId; audit.previousVersionId=assessment.previousVersionId;
            audit.recoveredVersionId=v.id; audit.source=assessment.source;
            em.persist(audit);
            if (assessment.showId == null) {
                for (var old : versions.findByScreenIdOrderByVersionNumberDesc(screenId)) {
                    if (!old.id.equals(v.id) && old.status == LayoutVersionStatus.ACTIVE) {
                        old.status=LayoutVersionStatus.ARCHIVED; old.activeScreenId=null;
                    }
                }
                versions.flush();
                v.status=LayoutVersionStatus.ACTIVE; v.activeScreenId=screenId;
                screen.activeLayoutVersionId=v.id; screen.layoutVersion=v.versionNumber;
                screen.rows=plan.seats.stream().mapToInt(x -> x.rowNumber).max().orElseThrow();
                screen.seatsPerRow=plan.seats.stream().mapToInt(x -> x.columnNumber).max().orElseThrow();
            } else {
                var show = shows.findById(assessment.showId).orElseThrow();
                show.layoutVersionId=v.id; show.layoutVersion=v.versionNumber;
                if (assessment.previousVersionId != null) {
                    versions.findById(assessment.previousVersionId).filter(old -> old.status == LayoutVersionStatus.DRAFT)
                            .ifPresent(old -> old.status = LayoutVersionStatus.ARCHIVED);
                }
            }
        }
        // Populate additive active metadata on otherwise healthy legacy records.
        var active = layouts.activeLayout(screenId);
        screen.activeLayoutVersionId=active.id; active.activeScreenId=screenId;
        versions.flush();
        return plans.stream().map(Plan::assessment).toList();
    }

    private LayoutVersion recovered(String screenId, List<LayoutSeat> input, boolean active) {
        var v=new LayoutVersion(); v.screenId=screenId;
        v.versionNumber=versions.findByScreenIdOrderByVersionNumberDesc(screenId).stream().mapToInt(x->x.versionNumber).max().orElse(0)+1;
        v.name="Recovered layout V"+v.versionNumber; v.status=active ? LayoutVersionStatus.DRAFT : LayoutVersionStatus.ARCHIVED;
        v.publishedAt=java.time.Instant.now(); v.snapshot="{}"; v=versions.saveAndFlush(v);
        final String recoveredId = v.id;
        var copy=input.stream().map(s->{var x=new LayoutSeat(); x.seatId=s.bookingSeatId(); x.layoutVersionId=recoveredId;
            x.label=s.label; x.rowNumber=s.rowNumber; x.columnNumber=s.columnNumber;
            x.category=s.category; x.color=s.color; x.disabled=s.disabled; return x;}).toList();
        seats.saveAllAndFlush(copy);
        try {v.snapshot=mapper.writeValueAsString(Map.of("seats",copy));}
        catch(Exception e){throw new ApiException(500,"LAYOUT_SNAPSHOT_ERROR","Unable to persist recovered snapshot");}
        return v;
    }

    private List<Plan> plans(String screenId, boolean adoptLegacy) {
        var screen=screens.findById(screenId).orElseThrow(()->new ApiException(404,"SCREEN_NOT_FOUND","Screen not found"));
        var result=new ArrayList<Plan>();
        var active=versions.findByScreenIdOrderByVersionNumberDesc(screenId).stream().filter(x->x.status==LayoutVersionStatus.ACTIVE).toList();
        var chosen=screen.activeLayoutVersionId==null ? (active.size()==1 ? active.get(0) : null)
                : versions.findById(screen.activeLayoutVersionId).filter(x->x.screenId.equals(screenId)).orElse(null);
        result.add(plan(screen,null,chosen,active.size()==1 && chosen!=null && active.get(0).id.equals(chosen.id),adoptLegacy));
        for(var show:shows.findByScreenId(screenId)) {
            var version=show.layoutVersionId==null
                    ? versions.findByScreenIdAndVersionNumber(screenId,show.layoutVersion).orElse(null)
                    : versions.findById(show.layoutVersionId).filter(x->x.screenId.equals(screenId)).orElse(null);
            result.add(plan(screen,show,version,version!=null && version.status!=LayoutVersionStatus.DRAFT,adoptLegacy));
        }
        return result;
    }

    private Plan plan(Screen screen, Show show, LayoutVersion version, boolean statusValid, boolean adoptLegacy) {
        var required=new HashSet<String>();
        if(show!=null) {
            bookings.findByShowId(show.id).forEach(b->required.addAll(b.seatIds));
            holds.findByShowId(show.id).forEach(h->required.add(h.seatId));
        }
        var candidate=version==null ? List.<LayoutSeat>of() : layouts.versionSeats(version.id);
        boolean valid=valid(candidate);
        boolean snapshotMatches=version==null || layouts.snapshotMatches(version,candidate);
        boolean referenceValid=show==null || (version!=null && version.id.equals(show.layoutVersionId));
        boolean consistent=statusValid && referenceValid && valid && snapshotMatches && candidate.stream().map(LayoutSeat::bookingSeatId).toList().containsAll(required);
        String source="VERSIONED_SEATS";
        if ((!valid || !snapshotMatches || !candidate.stream().map(LayoutSeat::bookingSeatId).toList().containsAll(required)) && version!=null) {
            try {
                var node=mapper.readTree(version.snapshot).get("seats");
                if(node!=null && node.isArray()) candidate=Arrays.asList(mapper.treeToValue(node,LayoutSeat[].class));
                source="VERSION_SNAPSHOT";
            } catch(Exception ignored) { source="VERSIONED_SEATS_WITH_INVALID_SNAPSHOT"; }
        }
        if (!valid(candidate) && adoptLegacy && (show==null || show.layoutVersion==screen.layoutVersion)) {
            candidate=screen.seats.stream().map(s->{var x=new LayoutSeat(); x.seatId=s.id;
                x.label=s.label; x.rowNumber=s.rowNumber; x.columnNumber=s.columnNumber;
                x.category=s.category; x.color=s.color; x.disabled=s.disabled; return x;}).toList();
            source="EXPLICIT_CURRENT_LEGACY_LAYOUT";
        }
        // Translate legacy identities only if geometry and properties match exactly.
        if(valid(candidate) && !candidate.stream().map(LayoutSeat::bookingSeatId).toList().containsAll(required)) {
            var translated=new ArrayList<LayoutSeat>();
            for(var seat:candidate) {
                var matches=screen.seats.stream().filter(old->old.label.equals(seat.label)
                        && old.rowNumber==seat.rowNumber && old.columnNumber==seat.columnNumber
                        && old.category.equals(seat.category) && old.disabled==seat.disabled
                        && Objects.equals(old.color,seat.color)).toList();
                var x=new LayoutSeat(); x.seatId=seat.bookingSeatId(); x.label=seat.label;
                x.rowNumber=seat.rowNumber; x.columnNumber=seat.columnNumber;
                x.category=seat.category; x.disabled=seat.disabled; x.color=seat.color;
                if(matches.size()==1 && required.contains(matches.get(0).id) && !required.contains(x.seatId)) x.seatId=matches.get(0).id;
                translated.add(x);
            }
            candidate=translated; source+="_LEGACY_ID_MATCH";
        }
        boolean repairable=valid(candidate) && candidate.stream().map(LayoutSeat::bookingSeatId).toList().containsAll(required);
        if(show==null && !statusValid && version==null && !adoptLegacy) repairable=false;
        return new Plan(new Assessment(show==null?null:show.id,show==null?(version==null?null:version.id):show.layoutVersionId,
                consistent,repairable,source,consistent?null:repairable?"Explicit version recovery required":"Ambiguous or missing seat map/seat references"),candidate);
    }

    private boolean valid(List<LayoutSeat> candidate) {
        try {layouts.validateSeats(candidate);return true;} catch(ApiException e){return false;}
    }
}
