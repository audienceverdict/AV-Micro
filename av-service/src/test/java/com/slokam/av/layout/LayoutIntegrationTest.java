package com.slokam.av.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.*;
import com.slokam.av.client.IdentityClient;
import com.slokam.av.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.Principal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=LayoutIntegrationTest.Config.class, properties={
    "spring.datasource.url=jdbc:h2:mem:layouts;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "logging.level.org.hibernate.SQL=OFF", "logging.level.com.slokam.av=INFO",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.flyway.enabled=false"})
@ActiveProfiles("layout-test")
class LayoutIntegrationTest {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @EntityScan("com.slokam.av.entity") @EnableJpaRepositories("com.slokam.av.repository")
    @Import({TheatreService.class, ShowService.class, ShowSeatService.class, BookingService.class,
        LayoutCompatibilityService.class, WaitingListService.class, TicketQrService.class})
    static class Config {}
    @MockitoBean NotificationService notifications;
    @Autowired TheatreService theatres;
    @Autowired ShowService showService;
    @Autowired ShowSeatService showSeats;
    @Autowired BookingService bookingService;
    @Autowired LayoutCompatibilityService compatibility;
    @Autowired ScreenRepository screens;
    @Autowired LayoutVersionRepository versions;
    @Autowired LayoutSeatRepository seats;
    @Autowired MovieRepository movies;
    @MockitoBean IdentityClient users;
    @Autowired ShowRepository shows;
    @Autowired SeatHoldRepository holds;
    @Autowired BookingRepository bookings;
    @Autowired TicketScanAuditRepository ticketScanAudits;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    record Fixture(Theatre theatre, Screen screen, Movie movie, User user) {
        Principal principal(){return ()->user.id;}
    }
    private Theatre theatre() {
        var t=new Theatre(); t.name="Theatre";t.address="Address";t.city="City";t.state="State";
        return theatres.create(t);
    }
    private Seat seat(String label, int column, boolean disabled) {
        var s=new Seat();s.id=UUID.randomUUID().toString();s.label=label;s.rowNumber=1;
        s.columnNumber=column;s.disabled=disabled;return s;
    }
    private Fixture fixture() {
        var t=theatre();var s=new Screen();s.name="Screen";s.number=1;s.rows=1;s.seatsPerRow=4;
        s.seats=new ArrayList<>(List.of(seat("A1",1,false),seat("A2",2,false),seat("A3",3,false),seat("A4",4,true)));
        s=theatres.createScreen(t.id,s);
        var m=new Movie();m.title="Movie";m.language="English";m.duration=90;m.status=MovieStatus.ACTIVE;
        m=movies.saveAndFlush(m);
        var u=new User();u.mobile=UUID.randomUUID().toString().substring(0,18);u.id=UUID.randomUUID().toString();u.enabled=true;org.mockito.Mockito.when(users.findById(u.id)).thenReturn(Optional.of(u));
        return new Fixture(t,s,m,u);
    }
    private Show show(Fixture f,int hour) {
        var s=new Show();s.screenId=f.screen.id;s.theatreId=f.theatre.id;s.movieId=f.movie.id;
        s.date=LocalDate.of(2026,12,1);s.startTime=LocalTime.of(hour,0);s.endTime=LocalTime.of(hour+1,0);
        s.ticketType=TicketType.FREE;return showService.create(s);
    }
    private List<LayoutSeat> layout(String version){return theatres.versionSeats(version);}
    private LayoutSeat input(LayoutSeat seat) throws Exception {
        return json.readValue(json.writeValueAsString(seat),LayoutSeat.class);
    }
    private SeatHoldRequest holdRequest(Show show,String id){var r=new SeatHoldRequest();r.showId=show.id;r.seatIds=List.of(id);return r;}
    private CreateBookingRequest bookingRequest(Show show,String... ids){var r=new CreateBookingRequest();r.showId=show.id;r.seatIds=List.of(ids);r.termsAccepted=true;return r;}
    private String geometry(String version) throws Exception {return json.writeValueAsString(layout(version));}

    @Test void listsScreensWithEmptyActiveLayoutsWithoutRelaxingShowValidation() {
        var f = fixture();
        seats.deleteAll(layout(f.screen.activeLayoutVersionId));
        var listed = theatres.screens(f.theatre.id);
        assertEquals(1, listed.size());
        assertEquals(f.screen.id, listed.get(0).id);
        assertTrue(listed.get(0).seats.isEmpty());
        assertThrows(ApiException.class, () -> theatres.activeLayout(f.screen.id));
    }

    @Test void listsLegacyScreensWithoutLayoutVersions() {
        var t = theatre();
        var s = new Screen(); s.theatreId = t.id; s.name = "Legacy"; s.number = 1;
        screens.saveAndFlush(s);
        var listed = theatres.screens(t.id);
        assertEquals(1, listed.size());
        assertEquals(s.id, listed.get(0).id);
        assertTrue(listed.get(0).seats.isEmpty());
    }

    @Test void initializesCompleteActiveLayoutAndRollsBackInvalidScreens() {
        var f=fixture();var v=theatres.activeLayout(f.screen.id);
        assertEquals(v.id,screens.findById(f.screen.id).orElseThrow().activeLayoutVersionId);
        assertEquals(f.screen.id,v.activeScreenId);assertEquals(4,layout(v.id).size());
        assertEquals(f.screen.seats.stream().map(x->x.id).toList(),layout(v.id).stream().map(LayoutSeat::bookingSeatId).toList());
        long before=screens.count();var empty=new Screen();empty.name="Empty";
        assertThrows(ApiException.class,()->theatres.createScreen(f.theatre.id,empty));assertEquals(before,screens.count());
    }

    @Test void draftEditsPreserveIdsAndPublishingLeavesOldShowUnchanged() throws Exception {
        var f=fixture();var oldShow=show(f,10);String original=geometry(oldShow.layoutVersionId);
        var draft=theatres.createLayoutVersion(f.screen.id,null,"Changed");
        assertEquals(layout(oldShow.layoutVersionId).stream().map(LayoutSeat::bookingSeatId).toList(),
                layout(draft.id).stream().map(LayoutSeat::bookingSeatId).toList());
        var edits=new ArrayList<LayoutSeat>();for(var seat:layout(draft.id))edits.add(input(seat));
        String stable=edits.get(0).bookingSeatId();edits.get(0).label="B1";edits.get(0).rowNumber=2;
        var extra=new LayoutSeat();extra.label="B2";extra.rowNumber=2;extra.columnNumber=2;edits.add(extra);
        theatres.saveLayoutSeats(f.screen.id,draft.id,edits);
        assertEquals(stable,layout(draft.id).stream().filter(x->x.label.equals("B1")).findFirst().orElseThrow().bookingSeatId());
        theatres.publishLayoutVersion(f.screen.id,draft.id);
        assertEquals(LayoutVersionStatus.ARCHIVED,versions.findById(oldShow.layoutVersionId).orElseThrow().status);
        assertEquals(original,geometry(oldShow.layoutVersionId));assertEquals(draft.id,show(f,12).layoutVersionId);
        assertEquals(oldShow.layoutVersionId,shows.findById(oldShow.id).orElseThrow().layoutVersionId);
        var response=showService.availability(oldShow.id);
        var displayedScreen=(Screen)response.get("screen");
        assertEquals(draft.id,displayedScreen.activeLayoutVersionId);
        assertEquals(4,displayedScreen.seats.size());
        assertEquals("A1",displayedScreen.seats.get(0).label);
        assertEquals(1,versions.findByScreenIdOrderByVersionNumberDesc(f.screen.id).stream().filter(v->v.status==LayoutVersionStatus.ACTIVE).count());
        assertThrows(ApiException.class,()->theatres.saveLayoutSeats(f.screen.id,oldShow.layoutVersionId,edits));
        assertThrows(ApiException.class,()->theatres.publishLayoutVersion(f.screen.id,oldShow.layoutVersionId));
    }

    @Test void rejectsInvalidDraftsAndReferencedDraftsWithoutLosingSeats() throws Exception {
        var f=fixture();var draft=theatres.createLayoutVersion(f.screen.id,null,null);String before=geometry(draft.id);
        assertThrows(ApiException.class,()->theatres.saveLayoutSeats(f.screen.id,draft.id,List.of()));
        var a=input(layout(draft.id).get(0));var b=input(layout(draft.id).get(1));b.label=a.label;
        assertThrows(ApiException.class,()->theatres.saveLayoutSeats(f.screen.id,draft.id,List.of(a,b)));
        b.label="Different";b.columnNumber=a.columnNumber;
        assertThrows(ApiException.class,()->theatres.saveLayoutSeats(f.screen.id,draft.id,List.of(a,b)));
        a.category="UNKNOWN";assertThrows(ApiException.class,()->theatres.saveLayoutSeats(f.screen.id,draft.id,List.of(a)));
        a.category="REGULAR";a.rowNumber=0;assertThrows(ApiException.class,()->theatres.saveLayoutSeats(f.screen.id,draft.id,List.of(a)));
        assertEquals(before,geometry(draft.id));
        var show=show(f,10);show.layoutVersionId=draft.id;shows.saveAndFlush(show);
        assertThrows(ApiException.class,()->theatres.saveLayoutSeats(f.screen.id,draft.id,layout(draft.id)));
        assertThrows(ApiException.class,()->theatres.publishLayoutVersion(f.screen.id,draft.id));
    }

    @Test void bookingHoldsExpiryAndCancellationOnlyChangeOccupancy() throws Exception {
        var f=fixture();var show=show(f,10);String before=geometry(show.layoutVersionId);var map=layout(show.layoutVersionId);
        String first=map.get(0).bookingSeatId(),second=map.get(1).bookingSeatId(),third=map.get(2).bookingSeatId();
        var held=bookingService.hold(f.principal(),holdRequest(show,third)).get(0);
        var booking=bookingService.create(f.principal(),bookingRequest(show,first,second));
        var states=showSeats.occupancy(show).seats();assertEquals(4,states.size());
        assertEquals(List.of("RESERVED","RESERVED","HELD","DISABLED"),states.stream().map(ShowSeatService.SeatState::state).toList());
        // Compare persisted timestamps: the database stores less precision than Instant.now().
        var persistedHold=holds.findById(held.id).orElseThrow();
        assertEquals(persistedHold.expiresAt,states.get(2).holdExpiresAt());assertTrue(states.stream().noneMatch(ShowSeatService.SeatState::clickable));
        bookingService.cancel(booking.id,f.principal(),List.of(first));
        assertEquals("AVAILABLE",showSeats.occupancy(show).seats().get(0).state());
        bookingService.cancel(booking.id,f.principal(),List.of(second));
        held.expiresAt=Instant.now().minusSeconds(1);holds.saveAndFlush(held);bookingService.expireHolds();
        assertEquals(3,showService.availableSeatCount(show.id));assertEquals(before,geometry(show.layoutVersionId));
        assertEquals(4,((List<?>)((Map<?,?>)showService.availability(show.id).get("layout")).get("seats")).size());
    }

    @Test void rejectsDisabledForeignSeatsAndOtherUsersHoldsOnCapturedVersion() throws Exception {
        var f=fixture();var show=show(f,10);var map=layout(show.layoutVersionId);
        assertThrows(ApiException.class,()->bookingService.hold(f.principal(),holdRequest(show,map.get(3).bookingSeatId())));
        assertThrows(ApiException.class,()->bookingService.create(f.principal(),bookingRequest(show,map.get(3).bookingSeatId())));
        bookingService.hold(f.principal(),holdRequest(show,map.get(0).bookingSeatId()));
        var other=new User();other.mobile=UUID.randomUUID().toString().substring(0,18);other.id=UUID.randomUUID().toString();other.enabled=true;org.mockito.Mockito.when(users.findById(other.id)).thenReturn(Optional.of(other));
        assertThrows(ApiException.class,()->bookingService.create(()->other.id,bookingRequest(show,map.get(0).bookingSeatId())));
        var draft=theatres.createLayoutVersion(f.screen.id,null,null);var edits=new ArrayList<>(layout(draft.id));
        var extra=new LayoutSeat();extra.label="B1";extra.rowNumber=2;extra.columnNumber=1;edits.add(extra);
        var saved=theatres.saveLayoutSeats(f.screen.id,draft.id,edits);var foreign=saved.stream().filter(x->x.label.equals("B1")).findFirst().orElseThrow().bookingSeatId();
        theatres.publishLayoutVersion(f.screen.id,draft.id);
        assertThrows(ApiException.class,()->bookingService.create(f.principal(),bookingRequest(show,foreign)));
        assertThrows(ApiException.class,()->bookingService.hold(f.principal(),holdRequest(show,foreign)));
        var booked=bookingService.create(f.principal(),bookingRequest(show,map.get(1).bookingSeatId()));
        assertEquals(show.id,booked.showId);assertEquals(4,showSeats.layout(show).size());
    }

    @Test void concurrentPublishingStillLeavesExactlyOneActiveVersion() throws Exception {
        var f=fixture();var a=theatres.createLayoutVersion(f.screen.id,null,"A");var b=theatres.createLayoutVersion(f.screen.id,null,"B");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var one=pool.submit(()->{start.await();return theatres.publishLayoutVersion(f.screen.id,a.id);});
            var two=pool.submit(()->{start.await();return theatres.publishLayoutVersion(f.screen.id,b.id);});
            start.countDown();one.get(10,java.util.concurrent.TimeUnit.SECONDS);two.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        var active=theatres.activeLayout(f.screen.id);assertTrue(Set.of(a.id,b.id).contains(active.id));
        assertEquals(1,versions.findByScreenIdOrderByVersionNumberDesc(f.screen.id).stream().filter(v->v.status==LayoutVersionStatus.ACTIVE).count());
        assertEquals(active.id,screens.findById(f.screen.id).orElseThrow().activeLayoutVersionId);
    }

    @Test void explicitRecoveryUsesSnapshotAndPreservesBookingsAndOriginalVersion() throws Exception {
        var f=fixture();var show=show(f,10);var booking=bookingService.create(f.principal(),bookingRequest(show,layout(show.layoutVersionId).get(0).bookingSeatId()));
        String old=show.layoutVersionId;String snapshot=versions.findById(old).orElseThrow().snapshot;
        new TransactionTemplate(transactionManager).executeWithoutResult(status->seats.deleteVersionSeats(old));
        assertThrows(ApiException.class,()->showService.availability(show.id));
        assertTrue(compatibility.assess(f.screen.id,false).stream().allMatch(LayoutCompatibilityService.Assessment::repairable));
        compatibility.reconcile(f.screen.id,false);
        var repaired=shows.findById(show.id).orElseThrow();assertNotEquals(old,repaired.layoutVersionId);
        assertEquals(snapshot,versions.findById(old).orElseThrow().snapshot);assertTrue(layout(old).isEmpty());
        assertEquals(booking.seatIds,bookings.findById(booking.id).orElseThrow().seatIds);
        assertEquals("RESERVED",showSeats.occupancy(repaired).seats().get(0).state());
        assertTrue(compatibility.assess(f.screen.id,false).stream().allMatch(LayoutCompatibilityService.Assessment::consistent));
    }

    @Test void missingLegacyVersionRequiresExplicitAdoptionAndRejectsOrphanIds() {
        var f=fixture();var show=show(f,10);
        var old=versions.findById(show.layoutVersionId).orElseThrow();old.snapshot="{}";versions.saveAndFlush(old);
        new TransactionTemplate(transactionManager).executeWithoutResult(status->seats.deleteVersionSeats(old.id));
        show.layoutVersionId=null;shows.saveAndFlush(show);
        assertFalse(compatibility.assess(f.screen.id,false).stream().filter(x->show.id.equals(x.showId())).findFirst().orElseThrow().repairable());
        assertThrows(ApiException.class,()->compatibility.reconcile(f.screen.id,false));
        compatibility.reconcile(f.screen.id,true);assertNotNull(shows.findById(show.id).orElseThrow().layoutVersionId);
        var held=new SeatHold();held.showId=show.id;held.userId=f.user.id;held.seatId="orphan";held.expiresAt=Instant.now().plusSeconds(60);holds.saveAndFlush(held);
        assertThrows(ApiException.class,()->showService.availability(show.id));
        assertThrows(ApiException.class,()->compatibility.reconcile(f.screen.id,true));assertTrue(holds.existsById(held.id));
    }
    @Test void swapsLabelsAndPositionsWithoutRegeneratingIds() throws Exception {
        var f=fixture();var draft=theatres.createLayoutVersion(f.screen.id,null,null);
        var edit=new ArrayList<LayoutSeat>();for(var seat:layout(draft.id))edit.add(input(seat));
        var a=edit.get(0);var b=edit.get(1);var id=a.bookingSeatId();
        a.label="A2";a.columnNumber=2;b.label="A1";b.columnNumber=1;
        theatres.saveLayoutSeats(f.screen.id,draft.id,edit);
        assertEquals(id,layout(draft.id).get(1).bookingSeatId());
        var replacement=new LayoutSeat();replacement.label="A2";replacement.rowNumber=1;replacement.columnNumber=2;
        theatres.saveLayoutSeats(f.screen.id,draft.id,List.of(replacement));
        assertEquals(id,layout(draft.id).get(0).bookingSeatId());
    }

    @Test void databaseConstraintRejectsTwoActiveScreenSlots() {
        var f=fixture();var draft=theatres.createLayoutVersion(f.screen.id,null,null);
        draft.status=LayoutVersionStatus.ACTIVE; // Entity lifecycle fills the unique active-screen slot.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->versions.saveAndFlush(draft));
        assertEquals(LayoutVersionStatus.DRAFT,versions.findById(draft.id).orElseThrow().status);
        assertEquals(f.screen.activeLayoutVersionId,theatres.activeLayout(f.screen.id).id);
    }

    @Test void legacyScreenSavePublishesNewMapAndOldBookingsRemainOnOldMap() throws Exception {
        var f=fixture();var show=show(f,10);String before=geometry(show.layoutVersionId);
        var next=screens.findById(f.screen.id).orElseThrow();
        next.seats.add(seat("B1",5,false));
        var saved=theatres.updateScreen(f.screen.id,next);
        assertEquals(5,saved.seats.size());assertNotEquals(show.layoutVersionId,saved.activeLayoutVersionId);
        assertEquals(before,geometry(show.layoutVersionId));assertEquals(4,showSeats.occupancy(show).seats().size());
        assertEquals(5,theatres.screens(f.theatre.id).get(0).seats.size());
        assertThrows(ApiException.class,()->theatres.deleteScreen(f.screen.id));
    }

    @Test void nullStableIdsFromLegacyVersionRemainBookableAfterDraftCopy() {
        var f=fixture();var show=show(f,10);var original=layout(show.layoutVersionId).get(0);
        // Reproduce old entity rows: booking identity was the existing primary key.
        original.seatId=null;seats.saveAndFlush(original);
        var draft=theatres.createLayoutVersion(f.screen.id,null,null);
        assertEquals(original.id,layout(draft.id).get(0).bookingSeatId());
        bookingService.hold(f.principal(),holdRequest(show,original.id));
        assertEquals(List.of(original.id),bookingService.create(f.principal(),bookingRequest(show,original.id)).seatIds);
    }

    @Test void publishFailureRetainsPreviousActiveVersion() {
        var f=fixture();var active=theatres.activeLayout(f.screen.id);var draft=theatres.createLayoutVersion(f.screen.id,null,null);
        new TransactionTemplate(transactionManager).executeWithoutResult(status->seats.deleteVersionSeats(draft.id));
        assertThrows(ApiException.class,()->theatres.publishLayoutVersion(f.screen.id,draft.id));
        assertEquals(active.id,theatres.activeLayout(f.screen.id).id);
        assertEquals(LayoutVersionStatus.DRAFT,versions.findById(draft.id).orElseThrow().status);
    }

    @Test void detectsAndRecoversPartialSeatMapsFromSnapshot() {
        var f=fixture();var show=show(f,10);String original=show.layoutVersionId;
        seats.deleteById(layout(original).get(2).id);
        assertThrows(ApiException.class,()->showService.availability(show.id));
        assertTrue(compatibility.assess(f.screen.id,false).stream().allMatch(LayoutCompatibilityService.Assessment::repairable));
        compatibility.reconcile(f.screen.id,false);
        assertEquals(4,showSeats.layout(shows.findById(show.id).orElseThrow()).size());
        assertEquals(3,layout(original).size()); // Original incomplete version remains as evidence.
    }

    @Test void serializesCompetingBookingsForOneSeat() throws Exception {
        var f=fixture();var show=show(f,10);var id=layout(show.layoutVersionId).get(0).bookingSeatId();
        var other=new User();other.mobile=UUID.randomUUID().toString().substring(0,18);other.id=UUID.randomUUID().toString();other.enabled=true;org.mockito.Mockito.when(users.findById(other.id)).thenReturn(Optional.of(other));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var one=pool.submit(()->{start.await();try {bookingService.create(f.principal(),bookingRequest(show,id));return true;}catch(ApiException e){assertEquals(409,e.status);return false;}});
            var two=pool.submit(()->{start.await();try {bookingService.create(()->other.id,bookingRequest(show,id));return true;}catch(ApiException e){assertEquals(409,e.status);return false;}});
            start.countDown();assertNotEquals(one.get(10,java.util.concurrent.TimeUnit.SECONDS),two.get(10,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,bookings.findByShowId(show.id).size());assertEquals("RESERVED",showSeats.occupancy(show).seats().get(0).state());
    }

    @Test void adminCancellationReleasesOccupancyWithoutChangingLayout() throws Exception {
        var f=fixture();var show=show(f,10);String before=geometry(show.layoutVersionId);
        var map=layout(show.layoutVersionId);
        var booking=bookingService.create(f.principal(),bookingRequest(show,map.get(0).bookingSeatId(),map.get(1).bookingSeatId()));
        bookingService.adminCancel(booking.id,"Requested",List.of(map.get(0).bookingSeatId()));
        assertEquals(2,showService.availableSeatCount(show.id));
        bookingService.adminCancel(booking.id,"Requested",null);
        assertEquals(3,showService.availableSeatCount(show.id));assertEquals(before,geometry(show.layoutVersionId));
    }

    @Test void showCreationAndPublishingCaptureAConsistentVersion() throws Exception {
        var f=fixture();var old=theatres.activeLayout(f.screen.id);var draft=theatres.createLayoutVersion(f.screen.id,null,null);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var creation=pool.submit(()->{start.await();return show(f,10);});
            var publishing=pool.submit(()->{start.await();return theatres.publishLayoutVersion(f.screen.id,draft.id);});
            start.countDown();var created=creation.get(10,java.util.concurrent.TimeUnit.SECONDS);
            publishing.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(Set.of(old.id,draft.id).contains(created.layoutVersionId));
            assertEquals(4,showSeats.layout(created).size());
            assertEquals(versions.findById(created.layoutVersionId).orElseThrow().versionNumber,created.layoutVersion);
        }
    }

    @Test void recoversMissingShowReferenceFromUniqueNumericVersion() {
        var f=fixture();var show=show(f,10);var originalId=layout(show.layoutVersionId).get(0).bookingSeatId();
        show.layoutVersionId=null;shows.saveAndFlush(show);
        assertTrue(compatibility.assess(f.screen.id,false).stream().allMatch(LayoutCompatibilityService.Assessment::repairable));
        compatibility.reconcile(f.screen.id,false);
        var recovered=shows.findById(show.id).orElseThrow();
        assertNotNull(recovered.layoutVersionId);assertEquals(originalId,showSeats.layout(recovered).get(0).bookingSeatId());
    }

    @Test void venueSetupDimensionsOnlyRequestCreatesCompleteActiveLayout() {
        var theatre=theatre();var request=new Screen();request.name="Screen 1";request.number=1;
        request.rows=6;request.seatsPerRow=10;request.seats=new ArrayList<>();
        var created=theatres.createScreen(theatre.id,request);
        assertEquals(60,created.seats.size());
        var active=theatres.activeLayout(created.id);var stored=layout(active.id);
        assertEquals(60,stored.size());assertEquals("A1",stored.get(0).label);
        assertEquals("F10",stored.get(59).label);
        assertEquals(created.seats.stream().map(x->x.id).toList(),stored.stream().map(LayoutSeat::bookingSeatId).toList());
        assertTrue(stored.stream().allMatch(x->!x.disabled && "REGULAR".equals(x.category)));
        var movie=new Movie();movie.title="Movie";movie.language="English";movie.duration=90;movie.status=MovieStatus.ACTIVE;
        movie=movies.saveAndFlush(movie);var user=new User();user.mobile=UUID.randomUUID().toString().substring(0,18);user.id=UUID.randomUUID().toString();user.enabled=true;org.mockito.Mockito.when(users.findById(user.id)).thenReturn(Optional.of(user));
        var fixture=new Fixture(theatre,created,movie,user);var show=show(fixture,10);
        assertEquals(active.id,show.layoutVersionId);assertEquals(60,showSeats.occupancy(show).seats().size());
        var booking=bookingService.create(fixture.principal(),bookingRequest(show,stored.get(0).bookingSeatId()));
        assertEquals(59,showService.availableSeatCount(show.id));
        bookingService.cancel(booking.id,fixture.principal(),null);
        assertEquals(60,showService.availableSeatCount(show.id));assertEquals(60,layout(active.id).size());
    }

    @Test void verifiesWholeBookingOnceAndRejectsRepeatedScan() {
        var f = fixture();
        var show = show(f, 10);
        var seatIds = layout(show.layoutVersionId).stream()
                .filter(seat -> !seat.disabled)
                .limit(2)
                .map(LayoutSeat::bookingSeatId)
                .toArray(String[]::new);
        var booking = bookingService.create(f.principal(), bookingRequest(show, seatIds));
        var payload = "AVT1:" + booking.ticketCode;

        assertEquals(
                "WRONG_SHOW",
                bookingService.verifyTicket(payload, UUID.randomUUID().toString(), "admin-id")
                        .result());
        var admitted = bookingService.verifyTicket(payload, show.id, "admin-id");
        assertTrue(admitted.valid());
        assertEquals("ADMITTED", admitted.result());
        assertEquals(booking.id, admitted.bookingId());
        assertEquals(2, admitted.ticketCount());
        assertEquals(2, admitted.seatIds().size());
        assertEquals(
                "ALREADY_USED",
                bookingService.verifyTicket(payload, show.id, "admin-id").result());
        var scanResults =
                ticketScanAudits.findByBookingIdOrderByScannedAtDesc(booking.id).stream()
                        .map(audit -> audit.result)
                        .toList();
        assertEquals(3, scanResults.size());
        assertTrue(scanResults.containsAll(List.of("ALREADY_USED", "ADMITTED", "WRONG_SHOW")));
    }

    @Test void initializesNullSeatsAndAlphabeticLabelsWithoutChangingExplicitSeats() {
        var theatre=theatre();var request=new Screen();request.name="Tall screen";request.number=1;
        request.rows=27;request.seatsPerRow=1;request.seats=null;
        var created=theatres.createScreen(theatre.id,request);
        assertEquals("Z1",created.seats.get(25).label);assertEquals("AA1",created.seats.get(26).label);
        var custom=new Screen();custom.name="Custom";custom.number=2;custom.rows=6;custom.seatsPerRow=10;
        custom.seats=new ArrayList<>(List.of(seat("CUSTOM",3,true)));
        var saved=theatres.createScreen(theatre.id,custom);
        assertEquals(1,layout(saved.activeLayoutVersionId).size());assertEquals("CUSTOM",saved.seats.get(0).label);
        assertTrue(saved.seats.get(0).disabled);assertEquals(3,saved.seats.get(0).columnNumber);
    }

    @Test void rejectsInvalidAutomaticDimensionsWithoutPersistingScreen() {
        var theatre=theatre();long before=screens.count();
        var request=new Screen();request.name="Invalid";request.rows=1;request.seatsPerRow=0;
        assertThrows(ApiException.class,()->theatres.createScreen(theatre.id,request));
        request.rows=Integer.MAX_VALUE;request.seatsPerRow=Integer.MAX_VALUE;
        assertThrows(ApiException.class,()->theatres.createScreen(theatre.id,request));
        assertEquals(before,screens.count());
    }

}
