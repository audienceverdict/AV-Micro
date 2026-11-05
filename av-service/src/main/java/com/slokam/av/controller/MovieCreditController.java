package com.slokam.av.controller;
import com.slokam.av.dto.*;
import com.slokam.av.service.MovieCreditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Movie Credit")
@RestController
@RequestMapping("/api/v1/movies/{movieId}")
public class MovieCreditController {
    private final MovieCreditService service;
    public MovieCreditController(MovieCreditService service) { this.service = service; }
    @io.swagger.v3.oas.annotations.Operation(summary = "List all movie credits", description = "List all movie credits.")
    @GetMapping("/credits") public List<MovieCreditResponse> list(@PathVariable String movieId) { return service.list(movieId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "List cast credits ordered by billing", description = "List cast credits ordered by billing.")
    @GetMapping("/cast") public List<MovieCreditResponse> cast(@PathVariable String movieId) { return service.cast(movieId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "List crew credits grouped by department", description = "List crew credits grouped by department.")
    @GetMapping("/crew") public Map<String, List<MovieCreditResponse>> crew(@PathVariable String movieId) { return service.crew(movieId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Add a person to movie credits with role and optional character details", description = "Add a person to movie credits with role and optional character details.")
    @PostMapping("/credits") @ResponseStatus(HttpStatus.CREATED)
    public MovieCreditResponse add(@PathVariable String movieId, @Valid @RequestBody MovieCreditRequest r) { return service.add(movieId, r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Update a movie credit and character details", description = "Update a movie credit and character details.")
    @PutMapping("/credits/{creditId}") public MovieCreditResponse update(@PathVariable String movieId, @PathVariable String creditId, @Valid @RequestBody MovieCreditRequest r) { return service.update(movieId, creditId, r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Delete a movie credit", description = "Delete a movie credit.")
    @DeleteMapping("/credits/{creditId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String movieId, @PathVariable String creditId) { service.delete(movieId, creditId); }
}
