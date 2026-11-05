package com.slokam.av.layout;

import com.slokam.av.entity.*;
import jakarta.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies additive entity-driven update against the previous entity shape, without SQL scripts. */
class LayoutEntityUpgradeTest {
    @Entity(name="LegacyScreen") @Table(name="screens")
    static class LegacyScreen {
        @Id @Column(length=36) String id=UUID.randomUUID().toString();
        @Column(nullable=false,length=36) String theatreId="theatre";
        @Column(nullable=false,length=100) String name="Screen";
        @Column(nullable=false) int number=1;
        @Column(name="row_count",nullable=false) int rows=1;
        @Column(nullable=false) int seatsPerRow=1;
        @Column(nullable=false) int layoutVersion=1;
        @Column(nullable=false) boolean layoutLocked;
        @Column(nullable=false,length=20) String status="ACTIVE";
        @Column(nullable=false) Instant createdAt=Instant.now();
        @Column(nullable=false) Instant updatedAt=Instant.now();
    }
    @Entity(name="LegacyVersion") @Table(name="layout_versions")
    static class LegacyVersion {
        @Id @Column(length=36) String id=UUID.randomUUID().toString();
        @Column(nullable=false,length=36) String screenId;
        @Column(nullable=false) int versionNumber=1;
        @Column(nullable=false,length=120) String name="Layout V1";
        @Column(nullable=false,length=20) String status="ACTIVE";
        Instant publishedAt=Instant.now();
        @Lob @Column(nullable=false,columnDefinition="LONGTEXT") String snapshot="{}";
        @Column(nullable=false) Instant createdAt=Instant.now();
    }
    @Entity(name="LegacyVersionSeat") @Table(name="layout_seats")
    static class LegacyVersionSeat {
        @Id @Column(length=36) String id=UUID.randomUUID().toString();
        @Column(name="layout_version_id",nullable=false,length=36) String layoutVersionId;
        @Column(nullable=false,length=80) String label="A1";
        @Column(name="`row_number`",nullable=false) int rowNumber=1;
        @Column(name="column_number",nullable=false) int columnNumber=1;
        @Column(nullable=false,length=40) String category="REGULAR";
        @Column(nullable=false) boolean disabled;
        @Column(length=7) String color="#8B7A91";
    }

    private Configuration configuration(String url,String ddl) {
        return new Configuration().setProperty("hibernate.connection.url",url)
                .setProperty("hibernate.connection.driver_class","org.h2.Driver")
                .setProperty("hibernate.connection.username","sa")
                .setProperty("hibernate.connection.password","")
                .setProperty("hibernate.hbm2ddl.auto",ddl)
                .setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
    }
    private SessionFactory updated(String url) {
        return configuration(url,"update").addAnnotatedClass(Screen.class).addAnnotatedClass(Seat.class)
                .addAnnotatedClass(LayoutVersion.class).addAnnotatedClass(LayoutSeat.class)
                .addAnnotatedClass(LayoutRecovery.class).buildSessionFactory();
    }

    @Test void createsLayoutSeatsWithQuotedPositionConstraint() {
        String url="jdbc:h2:mem:layout_create_"+UUID.randomUUID();
        try(var factory=configuration(url,"create-drop")
                .setProperty("hibernate.hbm2ddl.halt_on_error","true")
                .addAnnotatedClass(LayoutSeat.class).buildSessionFactory();
                var session=factory.openSession()) {
            var first=new LayoutSeat();first.layoutVersionId="version";first.label="A1";
            var duplicate=new LayoutSeat();duplicate.layoutVersionId="version";duplicate.label="A2";
            var transaction=session.beginTransaction();
            session.persist(first);session.flush();
            session.persist(duplicate);
            assertThrows(jakarta.persistence.PersistenceException.class,session::flush);
            transaction.rollback();
        }
    }

    @Test void updatesExistingTablesAndPreservesLegacyIdentitiesAcrossRepeatedStartup() {
        String url="jdbc:h2:mem:layout_upgrade_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        var screen=new LegacyScreen();var version=new LegacyVersion();version.screenId=screen.id;
        var seat=new LegacyVersionSeat();seat.layoutVersionId=version.id;
        try(var factory=configuration(url,"create").addAnnotatedClass(LegacyScreen.class)
                .addAnnotatedClass(LegacyVersion.class).addAnnotatedClass(LegacyVersionSeat.class).buildSessionFactory();
                var session=factory.openSession()) {
            var transaction=session.beginTransaction();session.persist(screen);session.persist(version);session.persist(seat);transaction.commit();
        }
        for(int startup=0;startup<2;startup++) {
            try(var factory=updated(url);var session=factory.openSession()) {
                var loaded=session.find(LayoutSeat.class,seat.id);
                assertNotNull(loaded);assertEquals(seat.id,loaded.bookingSeatId());assertNull(loaded.seatId);
                assertEquals("A1",loaded.label);assertEquals(1,loaded.rowNumber);assertFalse(loaded.disabled);
                assertNull(session.find(Screen.class,screen.id).activeLayoutVersionId);
                assertEquals(LayoutVersionStatus.ACTIVE,session.find(LayoutVersion.class,version.id).status);
                assertEquals(1L,session.createQuery("select count(s) from LayoutSeat s",Long.class).getSingleResult());
            }
        }
    }
}
