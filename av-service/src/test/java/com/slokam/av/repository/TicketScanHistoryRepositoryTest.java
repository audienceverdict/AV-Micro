package com.slokam.av.repository;

import com.slokam.av.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class TicketScanHistoryRepositoryTest {
    @Autowired TicketScanHistoryRepository history;
    @Autowired TicketScanAuditRepository scans;
    @Autowired BookingRepository bookings;
    @Autowired ShowRepository shows;

    @Test void filtersPaginationAndUnidentifiedScansPreserveHistory() {
        var show = new Show();
        show.movieId="movie"; show.theatreId="theatre"; show.screenId="screen";
        show.date=LocalDate.of(2026,1,1); show.startTime=LocalTime.NOON; show.endTime=LocalTime.of(14,0);
        shows.saveAndFlush(show);
        var booking = new Booking(); booking.userId="customer"; booking.showId=show.id;
        booking.movie="Movie"; booking.theatre="Theatre"; booking.screen="Screen";
        booking.date="2026-01-01"; booking.time="12:00"; booking.mobile="1234567890";
        bookings.saveAndFlush(booking);
        var admitted = scan(booking.id,"verifier","ADMITTED",Instant.parse("2026-01-01T12:00:00Z"));
        var invalid = scan(null,"other","INVALID_QR",Instant.parse("2026-01-01T12:01:00Z"));
        var all = history.history(null,null,null,null,PageRequest.of(0,1));
        assertEquals(2,all.getTotalElements()); assertEquals(2,all.getTotalPages());
        assertEquals(invalid.id,all.getContent().getFirst().id());
        assertNull(all.getContent().getFirst().customerId());
        assertEquals(admitted.id,history.history(null,null,null,null,PageRequest.of(1,1)).getContent().getFirst().id());
        for (String[] filters : new String[][]{
                {"verifier",null,null,null}, {null,"customer",null,null},
                {null,null,show.id,null}, {null,null,null,"theatre"},
                {"verifier","customer",show.id,"theatre"}}) {
            var result=history.history(filters[0],filters[1],filters[2],filters[3],PageRequest.of(0,20));
            assertEquals(1,result.getTotalElements());
            assertEquals(admitted.id,result.getContent().getFirst().id());
            assertEquals("customer",result.getContent().getFirst().customerId());
        }
        assertTrue(history.history("other",null,show.id,null,PageRequest.of(0,20)).isEmpty());
        assertTrue(history.history(null,null,null,"missing",PageRequest.of(0,20)).isEmpty());
        assertEquals(1,history.history("other",null,null,null,PageRequest.of(0,20)).getTotalElements());
    }
    private TicketScanAudit scan(String bookingId,String staffId,String result,Instant time) {
        var scan=new TicketScanAudit(); scan.bookingId=bookingId; scan.staffId=staffId;
        scan.result=result; scan.scannedAt=time; scan.credentialHash="a".repeat(64);
        return scans.saveAndFlush(scan);
    }
}
