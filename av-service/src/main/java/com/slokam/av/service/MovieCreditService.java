package com.slokam.av.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.mapper.CatalogMapper;
import com.slokam.av.repository.*;
import com.slokam.av.exception.custom.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MovieCreditService {
    private final MovieCreditRepository credits;
    private final PersonRepository people;
    private final PersonSocialLinkRepository links;
    private final MovieRepository movies;
    private final MovieMediaService media;
    private final CatalogMapper mapper;
    private final ObjectMapper json;
    public MovieCreditService(MovieCreditRepository credits, PersonRepository people, PersonSocialLinkRepository links,
                              MovieRepository movies, MovieMediaService media, CatalogMapper mapper, ObjectMapper json) {
        this.credits = credits; this.people = people; this.links = links; this.movies = movies; this.media = media; this.mapper = mapper; this.json = json;
    }
    public List<MovieCreditResponse> list(String id) {
        if (!movies.existsById(id)) throw new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found");
        return responses(credits.findByMovieIdOrderByBillingOrderAscIdAsc(id));
    }
    public List<MovieCreditResponse> cast(String id) { return list(id).stream().filter(c -> c.creditType == CreditType.CAST).toList(); }
    public Map<String, List<MovieCreditResponse>> crew(String id) {
        Map<String, List<MovieCreditResponse>> grouped = new LinkedHashMap<>();
        list(id).stream().filter(c -> c.creditType == CreditType.CREW).forEach(c -> grouped.computeIfAbsent(c.department == null ? "Other" : c.department, k -> new ArrayList<>()).add(c));
        return grouped;
    }
    public Page<MovieCreditResponse> filmography(String id, int page, int size) {
        if (!people.existsById(id)) throw new ApiException(404, "PERSON_NOT_FOUND", "Person not found");
        var found = credits.findByPersonId(id, CatalogValues.page(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
        var rows = responses(found.getContent()); Map<String, MovieCreditResponse> byId = new HashMap<>(); rows.forEach(r -> byId.put(r.id, r));
        return found.map(c -> byId.get(c.id));
    }
    public Page<MovieResponse> moviesForPerson(String id, int page, int size) {
        if (!people.existsById(id)) throw new ApiException(404, "PERSON_NOT_FOUND", "Person not found");
        return movies.findAll((root, query, cb) -> {
            var sub = query.subquery(String.class); var c = sub.from(MovieCredit.class);
            sub.select(c.get("movieId")).where(cb.equal(c.get("personId"), id));
            return root.get("id").in(sub);
        }, CatalogValues.page(page, size, Sort.by("releaseDate").descending().and(Sort.by("title")))).map(mapper::movie);
    }
    private List<MovieCreditResponse> responses(List<MovieCredit> rows) {
        var ids = rows.stream().map(c -> c.personId).distinct().toList();
        var social = ids.isEmpty() ? List.<PersonSocialLink>of() : links.findByPersonIdIn(ids);
        Map<String, PersonResponse> profiles = new HashMap<>();
        people.findAllById(ids).forEach(p -> profiles.put(p.id, mapper.person(p, social.stream().filter(s -> s.personId.equals(p.id)).toList())));
        return rows.stream().sorted(Comparator.comparing((MovieCredit c) -> c.billingOrder, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(c -> c.id))
                .map(c -> mapper.credit(c, profiles.get(c.personId))).toList();
    }
    @Transactional public MovieCreditResponse add(String id, MovieCreditRequest r) {
        Movie m = media.lock(id); MovieCredit c = new MovieCredit(); c.movieId = id; apply(m, c, r); credits.saveAndFlush(c); sync(m); return responses(List.of(c)).getFirst();
    }
    @Transactional public MovieCreditResponse update(String id, String creditId, MovieCreditRequest r) {
        Movie m = media.lock(id); var c = find(id, creditId); apply(m, c, r); credits.flush(); sync(m); return responses(List.of(c)).getFirst();
    }
    @Transactional public void delete(String id, String creditId) { Movie m = media.lock(id); credits.delete(find(id, creditId)); credits.flush(); sync(m); }
    private MovieCredit find(String id, String creditId) { return credits.findById(creditId).filter(c -> c.movieId.equals(id)).orElseThrow(() -> new ApiException(404, "CREDIT_NOT_FOUND", "Movie credit not found")); }
    private void apply(Movie m, MovieCredit c, MovieCreditRequest r) {
        if (!people.existsById(r.personId)) throw new ApiException(404, "PERSON_NOT_FOUND", "Person not found");
        if (r.creditType == CreditType.CREW && (r.characterName != null || r.characterDescription != null || r.characterImageUrl != null
                || r.characterCategory != null || r.characterOccupation != null || r.characterRelationships != null || r.screenTimeMinutes != null || r.isMainCast || r.isCameo || r.isVoiceRole))
            throw CatalogValues.invalid("Character and performance fields apply only to CAST credits");
        if (r.screenTimeMinutes != null && r.screenTimeMinutes > m.duration) throw CatalogValues.invalid("screenTimeMinutes cannot exceed movie duration");
        if (r.characterRelationships != null && r.characterRelationships.size() > 20) throw CatalogValues.invalid("At most 20 character relationships are supported");
        String key = roleKey(r.creditType, CatalogValues.text(r.department), r.roleTitle.trim(), CatalogValues.text(r.characterName));
        if (credits.existsByMovieIdAndPersonIdAndRoleKeyAndIdNot(c.movieId, r.personId, key, c.id))
            throw new ApiException(409, "DUPLICATE_CREDIT", "This person already has this role and character in the movie");
        c.personId = r.personId; c.creditType = r.creditType; c.department = CatalogValues.text(r.department); c.roleTitle = r.roleTitle.trim();
        c.characterName = CatalogValues.text(r.characterName); c.roleKey = key;
        c.characterDescription = CatalogValues.text(r.characterDescription); c.characterImageUrl = CatalogValues.text(r.characterImageUrl);
        c.characterCategory = r.characterCategory; c.characterOccupation = CatalogValues.text(r.characterOccupation);
        try { c.characterRelationships = r.characterRelationships == null ? null : json.writeValueAsString(r.characterRelationships); }
        catch (Exception e) { throw CatalogValues.invalid("Invalid characterRelationships"); }
        if (c.characterRelationships != null && c.characterRelationships.length() > 10000) throw CatalogValues.invalid("characterRelationships exceeds 10000 characters");
        c.screenTimeMinutes = r.screenTimeMinutes; c.billingOrder = r.billingOrder; c.isMainCast = r.isMainCast; c.isCameo = r.isCameo; c.isVoiceRole = r.isVoiceRole;
    }
    public static String roleKey(CreditType type, String department, String role, String character) {
        try {
            String key = String.join("\u001f", type.name(), normal(department), normal(role), normal(character));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String normal(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    public void sync(Movie m) {
        var rows = credits.findByMovieIdOrderByBillingOrderAscIdAsc(m.id);
        Map<String, Person> profiles = new HashMap<>(); people.findAllById(rows.stream().map(c -> c.personId).distinct().toList()).forEach(p -> profiles.put(p.id, p));
        m.cast.clear(); m.cast.addAll(CatalogValues.distinct(rows.stream().filter(c -> c.creditType == CreditType.CAST).map(c -> profiles.get(c.personId).fullName).toList()));
        // Legacy field supports one name; the complete director list remains in crew credits.
        m.director = rows.stream().filter(c -> c.creditType == CreditType.CREW && c.roleTitle.equalsIgnoreCase("Director")).map(c -> profiles.get(c.personId).fullName).findFirst().orElse(null);
        m.updatedAt = java.time.Instant.now();
    }
    public void applyLegacy(Movie m, MovieRequest r) {
        if (r.cast != null || r.supplied("cast")) {
            var names = CatalogValues.distinct(r.cast);
            var existing = credits.findByMovieIdOrderByBillingOrderAscIdAsc(m.id).stream().filter(c -> c.creditType == CreditType.CAST).toList();
            Map<String, Person> profiles = new HashMap<>(); people.findAllById(existing.stream().map(c -> c.personId).toList()).forEach(p -> profiles.put(p.id, p));
            for (var c : existing) if (names.stream().noneMatch(n -> n.equalsIgnoreCase(profiles.get(c.personId).fullName))) credits.delete(c);
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                var match = existing.stream().filter(c -> name.equalsIgnoreCase(profiles.get(c.personId).fullName)).toList();
                if (match.isEmpty()) { var p = legacyPerson(m.id, name); var c = legacyCredit(m.id, p.id, CreditType.CAST, null, "Actor"); c.billingOrder = i; credits.save(c); }
                else { int order = i; match.forEach(c -> c.billingOrder = order); }
            }
        }
        if (r.director != null || r.supplied("director")) {
            String name = CatalogValues.text(r.director);
            var directors = credits.findByMovieIdOrderByBillingOrderAscIdAsc(m.id).stream().filter(c -> c.creditType == CreditType.CREW && c.roleTitle.equalsIgnoreCase("Director")).toList();
            boolean already = name != null && directors.stream().anyMatch(c -> people.findById(c.personId).orElseThrow().fullName.equalsIgnoreCase(name));
            if (!already) {
                credits.deleteAll(directors); credits.flush();
                if (name != null) { var p = legacyPerson(m.id, name); credits.save(legacyCredit(m.id, p.id, CreditType.CREW, "Direction", "Director")); }
            }
        }
        credits.flush(); if (r.cast != null || r.director != null || r.supplied("cast") || r.supplied("director")) sync(m);
    }
    private Person legacyPerson(String movieId, String name) {
        // Identity is movie-scoped during import: a name alone cannot prove shared identity.
        String id = UUID.nameUUIDFromBytes((movieId + ":person:" + normal(name)).getBytes(StandardCharsets.UTF_8)).toString();
        return people.findById(id).orElseGet(() -> { Person p = new Person(); p.id = id; p.fullName = name; return people.save(p); });
    }
    private MovieCredit legacyCredit(String movieId, String personId, CreditType type, String department, String role) {
        MovieCredit c = new MovieCredit(); c.movieId = movieId; c.personId = personId; c.creditType = type; c.department = department; c.roleTitle = role;
        c.roleKey = roleKey(type, department, role, null); return c;
    }
}
