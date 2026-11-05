package com.slokam.av.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CatalogMapper {
    private final ObjectMapper json;
    public CatalogMapper(ObjectMapper json) { this.json = json; }

    // Public-field DTOs match the existing project's conventions. Copy only declared response
    // fields, never a JPA relationship or a persistence-only key.
    private <T> T copy(Object source, Class<T> type) {
        try {
            T target = type.getDeclaredConstructor().newInstance();
            for (var field : type.getFields()) {
                try {
                    Object value = source.getClass().getField(field.getName()).get(source);
                    field.set(target, value instanceof Set<?> set ? new LinkedHashSet<>(set) : value);
                } catch (NoSuchFieldException ignored) { /* Derived response field. */ }
            }
            return target;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    public MovieResponse movie(Movie m) {
        MovieResponse r = new MovieResponse();
        for (var field : Movie.class.getFields()) {
            try { MovieResponse.class.getField(field.getName()).set(r, field.get(m) instanceof Collection<?> c ? new ArrayList<>(c) : field.get(m)); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        r.posterUrl = com.slokam.av.validation.HttpUrlValidator.valid(m.posterUrl) ? m.posterUrl : null;
        r.posterImages = m.posterImages.stream().filter(com.slokam.av.validation.HttpUrlValidator::valid).distinct().toList();
        r.backdropUrl = com.slokam.av.validation.HttpUrlValidator.valid(m.backdropUrl) ? m.backdropUrl : null;
        r.trailerUrl = com.slokam.av.validation.HttpUrlValidator.valid(m.trailerUrl) ? m.trailerUrl : null;
        if (r.posterUrl == null && !r.posterImages.isEmpty()) r.posterUrl = r.posterImages.getFirst();
        r.durationMinutes = m.duration; r.synopsis = m.description;
        r.externalTrailerUrl = r.trailerUrl; r.genres = new ArrayList<>(m.genre);
        return r;
    }
    public MovieMediaResponse media(MovieMedia m) {
        MovieMediaResponse r = copy(m, MovieMediaResponse.class);
        r.embedUrl = com.slokam.av.service.VideoUrls.embed(m.mediaType, m.mediaUrl);
        return r;
    }
    public PersonResponse person(Person p, List<PersonSocialLink> links) {
        PersonResponse r = copy(p, PersonResponse.class);
        r.socialLinks = links.stream().map(s -> copy(s, PersonSocialLinkResponse.class)).toList();
        return r;
    }
    public PersonSocialLinkResponse social(PersonSocialLink s) { return copy(s, PersonSocialLinkResponse.class); }
    public MovieCreditResponse credit(MovieCredit c, PersonResponse person) {
        MovieCreditResponse r = new MovieCreditResponse();
        for (var field : MovieCredit.class.getFields()) {
            if (field.getName().equals("roleKey") || field.getName().equals("characterRelationships")) continue;
            try { MovieCreditResponse.class.getField(field.getName()).set(r, field.get(c)); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        if (c.characterRelationships != null) {
            try { r.characterRelationships = json.readValue(c.characterRelationships, new TypeReference<>() {}); }
            catch (Exception e) { throw new IllegalStateException("Invalid stored character relationships", e); }
        }
        r.person = person;
        return r;
    }
}
