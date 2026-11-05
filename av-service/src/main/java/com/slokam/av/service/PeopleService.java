package com.slokam.av.service;

import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.mapper.CatalogMapper;
import com.slokam.av.repository.*;
import com.slokam.av.exception.custom.ApiException;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PeopleService {
    private final PersonRepository people;
    private final PersonSocialLinkRepository links;
    private final CatalogMapper mapper;
    private final EntityManager em;
    private final MovieCreditRepository credits;
    private final MovieCreditService creditService;
    private final MovieMediaService media;
    public PeopleService(PersonRepository people, PersonSocialLinkRepository links, CatalogMapper mapper, EntityManager em, MovieCreditRepository credits, MovieCreditService creditService, MovieMediaService media) {
        this.people = people; this.links = links; this.mapper = mapper; this.em = em; this.credits = credits; this.creditService = creditService; this.media = media;
    }
    public Person getEntity(String id) { return people.findById(id).orElseThrow(() -> new ApiException(404, "PERSON_NOT_FOUND", "Person not found")); }
    public PersonResponse get(String id) { return mapper.person(getEntity(id), links.findByPersonIdOrderByIdAsc(id)); }
    public Page<PersonResponse> search(String name, int page, int size) {
        var p = CatalogValues.page(page, size, Sort.by("fullName").and(Sort.by("id")));
        var found = name == null ? people.findAll(p) : people.findByFullNameContainingIgnoreCase(name.trim(), p);
        var social = links.findByPersonIdIn(found.stream().map(x -> x.id).toList());
        return found.map(person -> mapper.person(person, social.stream().filter(l -> l.personId.equals(person.id)).toList()));
    }
    @Transactional public PersonResponse create(PersonRequest r) { Person p = new Person(); apply(p, r); people.save(p); return mapper.person(p, List.of()); }
    @Transactional public PersonResponse update(String id, PersonRequest r) { Person p = lock(id); apply(p, r); credits.findAllByPersonId(id).stream().map(c -> c.movieId).distinct().sorted().forEach(movieId -> creditService.sync(media.lock(movieId))); people.flush(); return mapper.person(p, links.findByPersonIdOrderByIdAsc(id)); }
    private void apply(Person p, PersonRequest r) {
        p.fullName = r.fullName.trim(); p.biography = CatalogValues.text(r.biography);
        p.profileImageUrl = CatalogValues.text(r.profileImageUrl); p.dateOfBirth = r.dateOfBirth;
        p.nationality = CatalogValues.text(r.nationality); p.officialWebsite = CatalogValues.text(r.officialWebsite);
        p.skills.clear(); p.skills.addAll(CatalogValues.distinct(r.skills));
    }
    private Person lock(String id) { Person p = em.find(Person.class, id, LockModeType.PESSIMISTIC_WRITE); if (p == null) throw new ApiException(404, "PERSON_NOT_FOUND", "Person not found"); return p; }
    public List<PersonSocialLinkResponse> social(String id) { getEntity(id); return links.findByPersonIdOrderByIdAsc(id).stream().map(mapper::social).toList(); }
    @Transactional public PersonSocialLinkResponse addSocial(String id, PersonSocialLinkRequest r) {
        lock(id); PersonSocialLink s = new PersonSocialLink(); s.personId = id; applySocial(s, r); return mapper.social(links.save(s));
    }
    @Transactional public PersonSocialLinkResponse updateSocial(String id, String linkId, PersonSocialLinkRequest r) {
        lock(id); var s = link(id, linkId); applySocial(s, r); links.flush(); return mapper.social(s);
    }
    private void applySocial(PersonSocialLink s, PersonSocialLinkRequest r) {
        if (links.findByPersonIdOrderByIdAsc(s.personId).stream().anyMatch(x -> !x.id.equals(s.id) && x.profileUrl.equals(r.profileUrl)))
            throw new ApiException(409, "DUPLICATE_SOCIAL_LINK", "Social URL already exists for this person");
        s.platform = r.platform; s.profileUrl = r.profileUrl; s.username = CatalogValues.text(r.username);
        s.isVerifiedOfficial = r.isVerifiedOfficial; // Explicit admin assertion; URL alone never verifies a profile.
    }
    private PersonSocialLink link(String id, String linkId) {
        return links.findById(linkId).filter(l -> l.personId.equals(id)).orElseThrow(() -> new ApiException(404, "SOCIAL_LINK_NOT_FOUND", "Social link not found"));
    }
    @Transactional public void deleteSocial(String id, String linkId) { lock(id); links.delete(link(id, linkId)); }
}
