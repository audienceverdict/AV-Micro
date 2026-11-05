package com.slokam.av.controller;
import com.slokam.av.dto.*;
import com.slokam.av.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "People")
@RestController
@RequestMapping("/api/v1/people")
public class PeopleController {
    private final PeopleService people;
    private final MovieCreditService credits;
    public PeopleController(PeopleService people, MovieCreditService credits) { this.people = people; this.credits = credits; }
    @io.swagger.v3.oas.annotations.Operation(summary = "Search people by optional name with zero-based pagination", description = "Search people by optional name with zero-based pagination.")
    @GetMapping public Page<PersonResponse> search(@RequestParam(required = false) String name, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return people.search(name, page, size); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Get a person profile and social links", description = "Get a person profile and social links.")
    @GetMapping("/{personId}") public PersonResponse get(@PathVariable String personId) { return people.get(personId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Create a person profile", description = "Create a person profile.")
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public PersonResponse create(@Valid @RequestBody PersonRequest r) { return people.create(r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Update a person profile", description = "Update a person profile.")
    @PutMapping("/{personId}") public PersonResponse update(@PathVariable String personId, @Valid @RequestBody PersonRequest r) { return people.update(personId, r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "List person social links", description = "List person social links.")
    @GetMapping("/{personId}/social-links") public List<PersonSocialLinkResponse> social(@PathVariable String personId) { return people.social(personId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Add a social profile link with platform and valid HTTP/HTTPS URL", description = "Add a social profile link with platform and valid HTTP/HTTPS URL.")
    @PostMapping("/{personId}/social-links") @ResponseStatus(HttpStatus.CREATED) public PersonSocialLinkResponse addSocial(@PathVariable String personId, @Valid @RequestBody PersonSocialLinkRequest r) { return people.addSocial(personId, r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Update a social profile link", description = "Update a social profile link.")
    @PutMapping("/{personId}/social-links/{linkId}") public PersonSocialLinkResponse updateSocial(@PathVariable String personId, @PathVariable String linkId, @Valid @RequestBody PersonSocialLinkRequest r) { return people.updateSocial(personId, linkId, r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Delete a social profile link", description = "Delete a social profile link.")
    @DeleteMapping("/{personId}/social-links/{linkId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSocial(@PathVariable String personId, @PathVariable String linkId) { people.deleteSocial(personId, linkId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "List a person movie credits with zero-based pagination", description = "List a person movie credits with zero-based pagination.")
    @GetMapping("/{personId}/filmography") public Page<MovieCreditResponse> filmography(@PathVariable String personId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return credits.filmography(personId, page, size); }
    @io.swagger.v3.oas.annotations.Operation(summary = "List movies associated with a person with zero-based pagination", description = "List movies associated with a person with zero-based pagination.")
    @GetMapping("/{personId}/movies") public Page<MovieResponse> movies(@PathVariable String personId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return credits.moviesForPerson(personId, page, size); }
}
