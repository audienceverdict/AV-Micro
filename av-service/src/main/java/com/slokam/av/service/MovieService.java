package com.slokam.av.service;

import com.slokam.av.entity.*;
import com.slokam.av.dto.*;
import com.slokam.av.repository.MovieRepository;
import com.slokam.av.mapper.CatalogMapper;
import com.slokam.av.exception.custom.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MovieService {
    private static final Logger log = LoggerFactory.getLogger(MovieService.class);
    private final MovieRepository movies;
    private final EntityManager em;
    private final MovieMediaService media;
    private final MovieCreditService credits;
    private final CatalogMapper mapper;
    public MovieService(MovieRepository movies, EntityManager em, MovieMediaService media, MovieCreditService credits, CatalogMapper mapper) {
        this.movies = movies; this.em = em; this.media = media; this.credits = credits; this.mapper = mapper;
    }
    public Page<MovieResponse> list(MovieStatus status, String title, String genre, String language, String certification,
                                    ReleaseStatus releaseStatus, ProductionStatus productionStatus, int page, int size) {
        return movies.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> filters = new ArrayList<>();
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (title != null && !title.isBlank()) filters.add(cb.like(cb.lower(root.get("title")), "%" + escape(title.trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
            if (genre != null && !genre.isBlank()) { query.distinct(true); filters.add(cb.equal(cb.lower(root.join("genre")), genre.trim().toLowerCase(Locale.ROOT))); }
            if (language != null && !language.isBlank()) {
                query.distinct(true);
                var languages = root.<Movie, String>join("languages", jakarta.persistence.criteria.JoinType.LEFT);
                filters.add(cb.or(cb.equal(cb.lower(root.get("language")), language.trim().toLowerCase(Locale.ROOT)), cb.equal(cb.lower(languages), language.trim().toLowerCase(Locale.ROOT))));
            }
            if (certification != null) filters.add(cb.equal(cb.lower(root.get("certification")), certification.trim().toLowerCase(Locale.ROOT)));
            if (releaseStatus != null) filters.add(cb.equal(root.get("releaseStatus"), releaseStatus));
            if (productionStatus != null) filters.add(cb.equal(root.get("productionStatus"), productionStatus));
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, CatalogValues.page(page, size, Sort.by("releaseDate").descending().and(Sort.by("title")).and(Sort.by("id")))).map(mapper::movie);
    }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    public Movie get(String id) { return movies.findById(id).orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found")); }
    public MovieResponse details(String id) {
        MovieResponse r = mapper.movie(get(id)); r.media = media.list(id);
        var all = credits.list(id); r.castCredits = all.stream().filter(c -> c.creditType == CreditType.CAST).toList();
        r.crewByDepartment = new LinkedHashMap<>();
        all.stream().filter(c -> c.creditType == CreditType.CREW).forEach(c -> r.crewByDepartment.computeIfAbsent(c.department == null ? "Other" : c.department, k -> new ArrayList<>()).add(c));
        return r;
    }
    @Transactional public MovieResponse create(MovieRequest r) {
        if (r.language == null || r.language.isBlank()) throw CatalogValues.invalid("language is required when creating a movie");
        if (r.duration == null) throw CatalogValues.invalid("duration or durationMinutes is required when creating a movie");
        Movie m = new Movie(); apply(m, r); m = movies.saveAndFlush(m); media.applyLegacy(m, r); credits.applyLegacy(m, r); return details(m.id);
    }
    @Transactional public MovieResponse update(String id, MovieRequest r) {
        Movie m = media.lock(id); apply(m, r); media.applyLegacy(m, r); credits.applyLegacy(m, r); return details(id);
    }
    private void apply(Movie m, MovieRequest r) {
        m.title = r.title.trim();
        if (r.language != null) { if (r.language.isBlank()) throw CatalogValues.invalid("language cannot be blank"); m.language = r.language.trim(); }
        if (r.duration != null) m.duration = r.duration;
        if (r.genre != null || r.supplied("genre")) { m.genre.clear(); m.genre.addAll(CatalogValues.distinct(r.genre)); }
        // Omitted properties preserve enhanced data when older clients submit their original forms.
        Set<String> excluded = Set.of("title", "language", "duration", "genre", "cast", "director", "posterUrl", "posterImages", "backdropUrl", "trailerUrl");
        for (var field : MovieRequest.class.getFields()) {
            if (excluded.contains(field.getName())) continue;
            try {
                Object value = field.get(r); if (value == null && !r.supplied(field.getName())) continue;
                if (field.getName().equals("status") && value == null) throw CatalogValues.invalid("status cannot be null");
                var target = Movie.class.getField(field.getName());
                if (java.util.Collection.class.isAssignableFrom(field.getType())) {
                    @SuppressWarnings("unchecked") Collection<String> destination = (Collection<String>) target.get(m);
                    destination.clear(); destination.addAll(CatalogValues.distinct((Collection<String>) value));
                } else target.set(m, value instanceof String s ? CatalogValues.text(s) : value);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        if (m.productionStartDate != null && m.productionEndDate != null && m.productionEndDate.isBefore(m.productionStartDate)) throw CatalogValues.invalid("productionEndDate must not precede productionStartDate");
        if (m.budget != null && m.currencyCode == null) throw CatalogValues.invalid("currencyCode is required with budget");
        if (m.currencyCode != null) { try { java.util.Currency.getInstance(m.currencyCode); } catch (IllegalArgumentException e) { throw CatalogValues.invalid("currencyCode must be a recognized ISO currency"); } }
        if (r.duration != null && movies.existsById(m.id)) {
            if (credits.list(m.id).stream().anyMatch(c -> c.screenTimeMinutes != null && c.screenTimeMinutes > m.duration)) throw CatalogValues.invalid("duration cannot be shorter than a credited screen time");
        }
        if ((r.production != null || r.supplied("production")) && r.productionCompanies == null && !r.supplied("productionCompanies")) { m.productionCompanies.clear(); if (CatalogValues.text(r.production) != null) m.productionCompanies.add(r.production.trim()); }
        if (r.productionCompanies != null || r.supplied("productionCompanies")) m.production = m.productionCompanies.stream().findFirst().orElse(null);
        m.updatedAt = java.time.Instant.now();
    }
    @Transactional
    public void delete(String id) {
        log.debug("Processing MovieService.delete");
        media.lock(id);
        em.createNativeQuery("delete from movie_media where movie_id=?1").setParameter(1, id).executeUpdate();
        em.createNativeQuery("delete from movie_credits where movie_id=?1").setParameter(1, id).executeUpdate();
        for (String table : java.util.List.of("movie_languages", "movie_filming_locations", "movie_production_companies"))
            em.createNativeQuery("delete from " + table + " where movie_id=?1").setParameter(1, id).executeUpdate();
        var shows =
                em.createNativeQuery("select id from shows where movie_id=?1")
                        .setParameter(1, id)
                        .getResultList();
        for (Object showId : shows) {
            em.createNativeQuery("delete from seat_holds where show_id=?1").setParameter(1, showId).executeUpdate();
            em.createNativeQuery("delete from waiting_list_entries where show_id=?1").setParameter(1, showId).executeUpdate();
            var bookings =
                    em.createNativeQuery("select id from bookings where show_id=?1")
                            .setParameter(1, showId)
                            .getResultList();
            for (Object bookingId : bookings) {
                em.createNativeQuery("delete from payments where booking_id=?1")
                        .setParameter(1, bookingId)
                        .executeUpdate();
                em.createNativeQuery("delete from booking_seats where booking_id=?1")
                        .setParameter(1, bookingId)
                        .executeUpdate();
            }
            em.createNativeQuery("delete from bookings where show_id=?1")
                    .setParameter(1, showId)
                    .executeUpdate();
        }
        em.createNativeQuery("delete from shows where movie_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from reviews where movie_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from movie_genres where movie_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from movie_cast where movie_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from movie_posters where movie_id=?1")
                .setParameter(1, id)
                .executeUpdate();
        em.createNativeQuery("delete from movies where id=?1").setParameter(1, id).executeUpdate();
        log.info("Movie deleted movieId={}", id);
    }
}
