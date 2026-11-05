package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import com.slokam.av.entity.CreditType;
import com.slokam.av.service.MovieCreditService;
import com.slokam.av.validation.HttpUrlValidator;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs once under Flyway. Stable IDs and existence checks also protect manual repair/retry. */
public class V3__Backfill_movie_profiles extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 2026101001; }
    @Override public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        List<Map<String, String>> movies = new ArrayList<>();
        try (var s = c.createStatement(); var rs = s.executeQuery("SELECT id, language, production, director, poster_url, trailer_url, backdrop_url FROM movies")) {
            while (rs.next()) {
                Map<String, String> row = new HashMap<>();
                for (String f : List.of("id", "language", "production", "director", "poster_url", "trailer_url", "backdrop_url")) row.put(f, rs.getString(f));
                movies.add(row);
            }
        }
        for (var m : movies) {
            String movie = m.get("id");
            if (text(m.get("language")) != null) collection(c, "movie_languages", movie, m.get("language").trim());
            // Production is retained as company text, never fabricated as a Person.
            if (text(m.get("production")) != null) collection(c, "movie_production_companies", movie, m.get("production").trim());
            List<String> posters = new ArrayList<>();
            if (text(m.get("poster_url")) != null) posters.add(m.get("poster_url"));
            try (var s = c.prepareStatement("SELECT poster_url FROM movie_posters WHERE movie_id=? ORDER BY poster_order")) {
                s.setString(1, movie); try (var rs = s.executeQuery()) { while (rs.next()) posters.add(rs.getString(1)); }
            }
            int order = 0; boolean primary = false;
            for (String url : new LinkedHashSet<>(posters)) {
                boolean inserted = media(c, movie, "POSTER", url, order++, !primary);
                if (inserted) primary = true;
            }
            media(c, movie, "TRAILER", m.get("trailer_url"), order++, false);
            media(c, movie, "STILL", m.get("backdrop_url"), order, false);
            Map<String, String> names = new LinkedHashMap<>();
            try (var s = c.prepareStatement("SELECT cast_name FROM movie_cast WHERE movie_id=?")) {
                s.setString(1, movie); try (var rs = s.executeQuery()) {
                    while (rs.next()) { String name = text(rs.getString(1)); if (name != null) names.putIfAbsent(name.toLowerCase(Locale.ROOT), name); }
                }
            }
            int billing = 0;
            for (String name : names.values()) credit(c, movie, name, CreditType.CAST, null, "Actor", billing++);
            if (text(m.get("director")) != null) credit(c, movie, m.get("director").trim(), CreditType.CREW, "Direction", "Director", 0);
            // UPCOMING/ACTIVE/ENDED remains the operational status; new statuses stay unknown.
        }
    }
    private static String text(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String id(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString(); }
    private static boolean exists(Connection c, String table, String key) throws SQLException {
        try (var s = c.prepareStatement("SELECT id FROM " + table + " WHERE id=?")) { s.setString(1, key); try (var r = s.executeQuery()) { return r.next(); } }
    }
    private static void collection(Connection c, String table, String movie, String value) throws SQLException {
        String column = table.equals("movie_languages") ? "language_name" : "company_name";
        try (var s = c.prepareStatement("SELECT movie_id FROM " + table + " WHERE movie_id=? AND " + column + "=?")) {
            s.setString(1, movie); s.setString(2, value); try (var r = s.executeQuery()) { if (r.next()) return; }
        }
        try (var s = c.prepareStatement("INSERT INTO " + table + " (movie_id," + column + ") VALUES (?,?)")) { s.setString(1, movie); s.setString(2, value); s.executeUpdate(); }
    }
    private static boolean media(Connection c, String movie, String type, String url, int order, boolean primary) throws SQLException {
        if (text(url) == null) return false;
        if (!HttpUrlValidator.valid(url)) {
            String key = id(movie + ":issue:" + type + ":" + url);
            if (!exists(c, "catalog_legacy_issues", key)) try (var s = c.prepareStatement("INSERT INTO catalog_legacy_issues (id,movie_id,field_name,original_value,reason,created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)")) {
                s.setString(1, key); s.setString(2, movie); s.setString(3, type); s.setString(4, url); s.setString(5, "Legacy value is not a supported HTTP/HTTPS URL; replace through media API"); s.executeUpdate();
            }
            return false;
        }
        String key = id(movie + ":media:" + type + ":" + url);
        if (!exists(c, "movie_media", key)) try (var s = c.prepareStatement("INSERT INTO movie_media (id,movie_id,media_type,media_url,is_primary,is_official,display_order,created_at,updated_at) VALUES (?,?,?,?,?,FALSE,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            s.setString(1, key); s.setString(2, movie); s.setString(3, type); s.setString(4, url); s.setBoolean(5, primary); s.setInt(6, order); s.executeUpdate();
        }
        return true;
    }
    private static void credit(Connection c, String movie, String name, CreditType type, String department, String role, int billing) throws SQLException {
        String person = id(movie + ":person:" + name.toLowerCase(Locale.ROOT));
        if (!exists(c, "people", person)) try (var s = c.prepareStatement("INSERT INTO people (id,full_name,created_at,updated_at) VALUES (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) { s.setString(1, person); s.setString(2, name); s.executeUpdate(); }
        String roleKey = MovieCreditService.roleKey(type, department, role, null);
        String key = id(movie + ":credit:" + person + ":" + roleKey);
        if (!exists(c, "movie_credits", key)) try (var s = c.prepareStatement("INSERT INTO movie_credits (id,movie_id,person_id,credit_type,department,role_title,billing_order,is_main_cast,is_cameo,is_voice_role,role_key,created_at,updated_at) VALUES (?,?,?,?,?,?,?,FALSE,FALSE,FALSE,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            s.setString(1, key); s.setString(2, movie); s.setString(3, person); s.setString(4, type.name()); s.setString(5, department); s.setString(6, role); s.setInt(7, billing); s.setString(8, roleKey); s.executeUpdate();
        }
    }
}
