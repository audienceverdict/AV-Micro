package com.slokam.av.controller;

import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Movie")
@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {
    private final MovieService service;
    public MovieController(MovieService service) { this.service = service; }
    @io.swagger.v3.oas.annotations.Operation(summary = "Search movies using optional catalog filters and zero-based pagination", description = "Search movies using optional catalog filters and zero-based pagination.")
    @GetMapping public Page<MovieResponse> list(
        @RequestParam(required = false) MovieStatus status,
        @RequestParam(required = false) String title,
        @RequestParam(required = false) String genre,
        @RequestParam(required = false) String language,
        @RequestParam(required = false) String certification,
        @RequestParam(required = false) ReleaseStatus releaseStatus,
        @RequestParam(required = false) ProductionStatus productionStatus,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return service.list(status, title, genre, language, certification, releaseStatus, productionStatus, page, size);
    }
    @io.swagger.v3.oas.annotations.Operation(summary = "Get movie details including media, cast and crew grouped by department", description = "Get movie details including media, cast and crew grouped by department.")
    @GetMapping("/{id}") public MovieResponse get(@PathVariable String id) { return service.details(id); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Create a movie catalog entry", description = "Create a movie catalog entry. HTTP/HTTPS URLs and field constraints are validated.")
    @PostMapping public MovieResponse create(@Valid @RequestBody MovieRequest movie) { return service.create(movie); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Update a movie catalog entry", description = "Update a movie catalog entry. Supported aliases include durationMinutes, synopsis, externalTrailerUrl and genres.")
    @PutMapping("/{id}") public MovieResponse update(@PathVariable String id, @Valid @RequestBody MovieRequest movie) { return service.update(id, movie); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Delete a movie subject to related record constraints", description = "Delete a movie subject to related record constraints.")
    @DeleteMapping("/{id}") public void delete(@PathVariable String id) { service.delete(id); }
}
