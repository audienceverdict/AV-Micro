package com.slokam.av.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CatalogMigrationTest {
    @Test void upgradesLegacyRecordsWithoutLosingDataAndCanRunAgain() throws Exception {
        String url = "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", ""); var s = c.createStatement()) {
            s.execute("CREATE TABLE users (id VARCHAR(36) PRIMARY KEY, role VARCHAR(20) NOT NULL)");
            s.execute("INSERT INTO users VALUES ('audience', 'USER'), ('admin', 'ADMIN')");
            s.executeUpdate("INSERT INTO movies (id,title,language,duration,status,poster_url,trailer_url,director,production,created_at,updated_at) VALUES ('m1','Legacy movie','Telugu',120,'ACTIVE','data:image/png;base64,KEEP-ME','https://youtu.be/dQw4w9WgXcQ','Same Name','Company Ltd',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO movies (id,title,language,duration,status,poster_url,director,created_at,updated_at) VALUES ('m2','Other movie','English',100,'ENDED','https://example.com/p2.jpg','Same Name',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO movie_posters VALUES ('m1',0,'https://example.com/p1.jpg'),('m1',1,'https://example.com/p1.jpg')");
            s.executeUpdate("INSERT INTO movie_cast VALUES ('m1','Same Name'),('m1',' Same Name '),('m1','Another Actor')");
            s.executeUpdate("INSERT INTO movie_genres VALUES ('m1','Drama')");
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (Connection c = DriverManager.getConnection(url, "sa", ""); var s = c.createStatement()) {
            assertEquals(2, count(s, "users"));
            assertThrows(SQLException.class, () -> count(s, "user_roles"));
            assertThrows(SQLException.class, () -> count(s, "user_verifier_shows"));
            assertThrows(SQLException.class, () -> count(s, "role_assignment_audits"));
            try (var r = s.executeQuery("SELECT role FROM users WHERE id='admin'")) {
                assertTrue(r.next()); assertEquals("ADMIN", r.getString(1));
            }
            assertEquals(2, count(s, "movies")); assertEquals(3, count(s, "people"));
            assertEquals(4, count(s, "movie_credits")); assertEquals(3, count(s, "movie_media")); assertEquals(1, count(s, "catalog_legacy_issues"));
            try (var r = s.executeQuery("SELECT poster_url,status,release_status,production FROM movies WHERE id='m1'")) {
                assertTrue(r.next()); assertEquals("data:image/png;base64,KEEP-ME", r.getString(1)); assertEquals("ACTIVE", r.getString(2)); assertNull(r.getString(3)); assertEquals("Company Ltd", r.getString(4));
            }
            try (var r = s.executeQuery("SELECT original_value FROM catalog_legacy_issues")) { assertTrue(r.next()); assertEquals("data:image/png;base64,KEEP-ME", r.getString(1)); }
            assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO movie_media (id,movie_id,media_type,media_url,is_primary,created_at,updated_at) VALUES ('duplicate','m1','POSTER','https://example.com/new.jpg',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
            assertThrows(SQLException.class, () -> s.executeUpdate("DELETE FROM people WHERE id=(SELECT person_id FROM movie_credits LIMIT 1)"));
            s.executeUpdate("DELETE FROM movies WHERE id='m2'"); assertEquals(3, count(s, "people"));
        }
    }
    @Test void baselinesAnExistingSchemaAtVersionOne() throws Exception {
        String url = "jdbc:h2:mem:baseline_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        // Simulate the legacy schema with no Flyway history, as on the current deployment.
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (var c = DriverManager.getConnection(url, "sa", ""); var s = c.createStatement()) { s.execute("DROP TABLE flyway_schema_history"); }
        assertEquals(2, Flyway.configure().dataSource(url, "sa", "").baselineOnMigrate(true).baselineVersion("1").load().migrate().migrationsExecuted);
    }
    private int count(Statement s, String table) throws SQLException { try (var r = s.executeQuery("SELECT COUNT(*) FROM " + table)) { r.next(); return r.getInt(1); } }
}
