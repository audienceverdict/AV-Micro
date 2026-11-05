package com.slokam.av.catalog;

import com.fasterxml.jackson.databind.*;
import com.slokam.av.config.SecurityConfig;
import com.slokam.av.controller.*;
import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.exception.handler.GlobalExceptionHandler;
import com.slokam.av.filter.JwtAuthenticationFilter;
import com.slokam.av.mapper.CatalogMapper;
import com.slokam.av.repository.*;
import com.slokam.av.security.*;
import com.slokam.av.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CatalogIntegrationTest.Config.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=update", "spring.jpa.open-in-view=false", "spring.flyway.enabled=true",
    "spring.jackson.deserialization.fail-on-unknown-properties=true", "app.cors-origins=http://localhost:3000"})
@ActiveProfiles("catalog-test")
@AutoConfigureMockMvc
class CatalogIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.slokam.av.entity")
    @EnableJpaRepositories("com.slokam.av.repository")
    @Import({MovieService.class, MovieMediaService.class, MovieCreditService.class, PeopleService.class, CatalogMapper.class,
            MovieController.class, MovieMediaController.class, MovieCreditController.class, PeopleController.class,
            GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
    static class Config {}
    @MockitoBean JwtService jwt;
    @MockitoBean CustomUserDetailsService users;
    @Autowired MovieService movies;
    @Autowired MovieMediaService media;
    @Autowired MovieCreditService credits;
    @Autowired PeopleService people;
    @Autowired PersonRepository personRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MovieRequest movieRequest(String title) {
        MovieRequest r = new MovieRequest(); r.title = title; r.language = "Telugu"; r.duration = 120;
        r.posterUrl = "https://example.com/poster.jpg"; r.genre = List.of("Drama", " drama "); r.status = MovieStatus.ACTIVE;
        return r;
    }
    private MovieMediaRequest mediaRequest(MovieMediaType type, String url) { MovieMediaRequest r = new MovieMediaRequest(); r.mediaType = type; r.mediaUrl = url; return r; }
    private PersonRequest personRequest(String name) { PersonRequest r = new PersonRequest(); r.fullName = name; r.profileImageUrl = "https://example.com/person.jpg"; r.skills = Set.of("Editing", "Cinematography"); return r; }
    private MovieCreditRequest creditRequest(String person, CreditType type, String role) { MovieCreditRequest r = new MovieCreditRequest(); r.personId = person; r.creditType = type; r.roleTitle = role; return r; }

    @Test void legacyApiAndEnhancedFieldsWorkWithClosedPersistenceSession() throws Exception {
        String body = """
            {"title":"Legacy API","language":"Telugu","duration":120,"status":"ACTIVE",
             "posterUrl":"https://example.com/p.jpg","posterImages":["https://example.com/p.jpg"],
             "genre":["Drama","drama"],"cast":["Actor One"],"director":"Director One","production":"Studio One",
             "trailerUrl":"https://youtu.be/dQw4w9WgXcQ","description":"Synopsis",
             "languages":["Telugu","English"],"originalTitle":"Original","releaseStatus":"DELAYED","productionStatus":"FILMING"}
            """;
        var result = mvc.perform(post("/api/v1/movies").with(user("admin").roles("ADMIN")).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duration").value(120)).andExpect(jsonPath("$.durationMinutes").value(120))
                .andExpect(jsonPath("$.description").value("Synopsis")).andExpect(jsonPath("$.synopsis").value("Synopsis"))
                .andExpect(jsonPath("$.genre.length()").value(1)).andExpect(jsonPath("$.cast[0]").value("Actor One"))
                .andExpect(jsonPath("$.castCredits[0].person.fullName").value("Actor One")).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(put("/api/v1/movies/" + id).with(user("admin").roles("ADMIN")).contentType("application/json")
                .content("{\"title\":\"Renamed\",\"duration\":121}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originalTitle").value("Original"))
                .andExpect(jsonPath("$.languages.length()").value(2)).andExpect(jsonPath("$.cast[0]").value("Actor One"));
        String roundTrip = mvc.perform(get("/api/v1/movies/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.crewByDepartment.Direction[0].roleTitle").value("Director")).andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/v1/movies/" + id).with(user("admin").roles("ADMIN")).contentType("application/json").content(roundTrip))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/movies").param("genre", "drama").param("language", "English").param("releaseStatus", "DELAYED").param("title", "Renamed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
    }
    @Test void managesMultipleMediaAndKeepsLegacyPosterAndTrailerInSync() {
        String id = movies.create(movieRequest("Media movie")).id;
        var p = mediaRequest(MovieMediaType.THEATRICAL_POSTER, "https://example.com/p2.jpg"); p.isPrimary = true; p.title = "Theatrical";
        var poster = media.add(id, p);
        var teaser = media.add(id, mediaRequest(MovieMediaType.TEASER, "https://youtu.be/dQw4w9WgXcQ"));
        assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", teaser.embedUrl);
        var trailer = mediaRequest(MovieMediaType.TRAILER, "https://example.com/video.mp4"); trailer.language = "English";
        var t = media.add(id, trailer); assertNull(t.embedUrl);
        for (var type : List.of(MovieMediaType.MAKING_OF, MovieMediaType.INTERVIEW, MovieMediaType.EVENT_VIDEO, MovieMediaType.STILL, MovieMediaType.EVENT_PHOTO)) media.add(id, mediaRequest(type, "https://example.com/" + type + ".asset"));
        assertEquals(p.mediaUrl, movies.details(id).posterUrl); assertEquals(trailer.mediaUrl, movies.details(id).externalTrailerUrl);
        assertEquals(1, media.list(id).stream().filter(x -> x.isPrimary).count());
        var ids = new ArrayList<>(media.list(id).stream().map(x -> x.id).toList()); Collections.reverse(ids);
        assertEquals(ids, media.reorder(id, ids).stream().map(x -> x.id).toList());
        media.delete(id, poster.id); assertEquals("https://example.com/poster.jpg", movies.details(id).posterUrl);
        assertThrows(ApiException.class, () -> media.primary(id, teaser.id));
        assertThrows(ApiException.class, () -> media.reorder(id, List.of(teaser.id, teaser.id)));
        var legacy = movieRequest("Media updated"); legacy.posterImages = List.of("https://example.com/replacement.jpg"); legacy.posterUrl = "https://example.com/replacement.jpg";
        movies.update(id, legacy); assertTrue(media.list(id).stream().anyMatch(x -> x.id.equals(teaser.id)));
    }
    @Test void managesReusableActorAndTechnicianProfilesAndDistinctRoles() {
        String movie = movies.create(movieRequest("Credits movie")).id;
        String movie2 = movies.create(movieRequest("Other credits movie")).id;
        var person = people.create(personRequest("Multi-skilled Person"));
        var actor = creditRequest(person.id, CreditType.CAST, "Actor"); actor.characterName = "Hero"; actor.isMainCast = true;
        actor.characterCategory = CharacterCategory.HERO; actor.characterImageUrl = "https://example.com/hero.jpg";
        actor.characterRelationships = Map.of("friend", "Other character"); actor.billingOrder = 0;
        var c = credits.add(movie, actor); assertEquals("Hero", c.characterName); assertEquals(actor.characterRelationships, c.characterRelationships);
        assertThrows(ApiException.class, () -> credits.add(movie, actor));
        var crew = creditRequest(person.id, CreditType.CREW, "Editor"); crew.department = "Post-production";
        credits.add(movie, crew); crew.roleTitle = "VFX supervisor"; credits.add(movie, crew); credits.add(movie2, crew);
        assertEquals(2, credits.crew(movie).get("Post-production").size()); assertEquals(4, credits.filmography(person.id, 0, 20).getTotalElements());
        assertEquals(2, credits.moviesForPerson(person.id, 0, 20).getTotalElements());
        var social = new PersonSocialLinkRequest(); social.platform = SocialPlatform.INSTAGRAM; social.profileUrl = "https://instagram.com/person";
        var link = people.addSocial(person.id, social); assertFalse(link.isVerifiedOfficial);
        social.isVerifiedOfficial = true; assertTrue(people.updateSocial(person.id, link.id, social).isVerifiedOfficial);
        assertEquals(2, people.get(person.id).skills.size());
        people.update(person.id, personRequest("Renamed Person")); assertEquals("Renamed Person", movies.details(movie).cast.getFirst());
        movies.delete(movie); assertTrue(personRepository.existsById(person.id)); assertEquals(1, credits.filmography(person.id, 0, 20).getTotalElements());
        people.deleteSocial(person.id, link.id); assertTrue(people.social(person.id).isEmpty());
    }
    @Test void validatesUrlsFieldsAndOwnershipAndRequiresAdministrators() throws Exception {
        String movie = movies.create(movieRequest("Validation movie")).id;
        String route = "/api/v1/movies/" + movie + "/media";
        for (String url : List.of("javascript:alert(1)", "data:image/png;base64,abc", "file:///tmp/p.jpg", "ftp://example.com/a", "https://user:password@example.com/a")) {
            mvc.perform(post(route).with(user("admin").roles("ADMIN")).contentType("application/json")
                    .content(json.writeValueAsString(mediaRequest(MovieMediaType.POSTER, url))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.mediaUrl").exists());
        }
        mvc.perform(post(route).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(post(route).with(user("viewer").roles("USER")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/people").with(user("viewer").roles("USER")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/people/any").with(user("viewer").roles("USER")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/movies/" + movie + "/credits/any").with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/movies").with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"title\":\"\",\"durationMinutes\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists()).andExpect(jsonPath("$.fieldErrors.duration").exists());
        mvc.perform(get("/api/v1/movies").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/movies").param("releaseStatus", "INVALID")).andExpect(status().isBadRequest());
        var poster = media.add(movie, mediaRequest(MovieMediaType.POSTER, "https://example.com/another.jpg"));
        String other = movies.create(movieRequest("Other validation movie")).id;
        assertThrows(ApiException.class, () -> media.delete(other, poster.id));
        assertThrows(Exception.class, () -> jdbc.update("UPDATE movie_media SET is_primary=TRUE WHERE id=?", poster.id));
    }
    @Test void malformedMoviePayloadReportsSpecificFieldsAndExpectedValues() throws Exception {
        String[][] cases = {
            {"{\"title\":\"Antahkarana\",\"countryOfOrigin\":[\"India\"]}",
                    "countryOfOrigin", "Expected a string"},
            {"{\"title\":\"Antahkarana\",\"productionCompanies\":[{\"name\":\"Antahkarana Pictures\"}]}",
                    "productionCompanies[0]", "Expected a string"},
            {"{\"title\":\"Antahkarana\",\"productionStatus\":\"In post-production\"}",
                    "productionStatus", "POST_PRODUCTION"},
            {"{\"title\":\"Antahkarana\",\"releaseStatus\":\"Release date to be announced\"}",
                    "releaseStatus", "UPCOMING"}
        };
        for (String[] invalid : cases) {
            mvc.perform(post("/api/v1/movies").with(user("admin").roles("ADMIN"))
                            .contentType("application/json").content(invalid[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                    .andExpect(jsonPath("$.path").value("/api/v1/movies"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(invalid[1])))
                    .andExpect(jsonPath("$.fieldErrors['" + invalid[1] + "']")
                            .value(org.hamcrest.Matchers.containsString(invalid[2])));
        }
    }
    @Test void concurrentPrimaryPosterChangesNeverCreateTwoPrimaries() throws Exception {
        String id = movies.create(movieRequest("Concurrent poster")).id;
        String first = media.add(id, mediaRequest(MovieMediaType.POSTER, "https://example.com/first.jpg")).id;
        String second = media.add(id, mediaRequest(MovieMediaType.POSTER, "https://example.com/second.jpg")).id;
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return media.primary(id, first); });
            var b = pool.submit(() -> { start.await(); return media.primary(id, second); });
            start.countDown(); a.get(10, java.util.concurrent.TimeUnit.SECONDS); b.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1, media.list(id).stream().filter(m -> m.isPrimary).count());
        assertTrue(Set.of("https://example.com/first.jpg", "https://example.com/second.jpg").contains(movies.details(id).posterUrl));
    }

    @Test void clearsOptionalFieldsWithoutErasingOmittedFieldsAndRejectsConflictingAliases() throws Exception {
        MovieRequest r = movieRequest("Clear fields"); r.originalTitle = "Keep"; r.announcementDate = java.time.LocalDate.of(2026, 1, 1);
        String id = movies.create(r).id;
        mvc.perform(put("/api/v1/movies/" + id).with(user("admin").roles("ADMIN")).contentType("application/json")
                .content("{\"title\":\"Cleared\",\"announcementDate\":null,\"posterImages\":[],\"releaseStatus\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.announcementDate").isEmpty())
                .andExpect(jsonPath("$.originalTitle").value("Keep")).andExpect(jsonPath("$.posterImages.length()").value(0));
        mvc.perform(post("/api/v1/movies").with(user("admin").roles("ADMIN")).contentType("application/json")
                .content("{\"title\":\"Conflict\",\"language\":\"English\",\"duration\":120,\"durationMinutes\":90}"))
                .andExpect(status().isBadRequest());
    }

    @Test void acceptsEnhancedAliasesAndProfilesWithoutPosters() throws Exception {
        mvc.perform(post("/api/v1/movies").with(user("admin").roles("ADMIN")).contentType("application/json")
                .content("{\"title\":\"Announced\",\"language\":\"Telugu\",\"durationMinutes\":90,\"synopsis\":\"New\",\"genres\":[\"Drama\"],\"externalTrailerUrl\":\"https://example.com/video.mp4\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duration").value(90)).andExpect(jsonPath("$.description").value("New"));
    }
}
