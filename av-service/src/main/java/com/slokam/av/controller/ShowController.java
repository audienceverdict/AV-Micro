package com.slokam.av.controller;

import com.slokam.av.entity.Show;
import com.slokam.av.service.ShowService;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Show")
@RestController
@RequestMapping("/api/v1/shows")
public class ShowController {
    private final ShowService service;

    public ShowController(ShowService service) {
        this.service = service;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List non-cancelled shows using the supported movie, theatre and date filters", description = "List non-cancelled shows using the supported movie, theatre and date filters. Dates use YYYY-MM-DD.")
    @GetMapping
    public List<Show> list(
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false) String theatreId,
            @RequestParam(required = false) LocalDate date) {
        return service.list(movieId, theatreId, date);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get a show by ID", description = "Get a show by ID.")
    @GetMapping("/{id}")
    public Show get(@PathVariable String id) {
        return service.get(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get show, screen, versioned layout, bookedSeatIds, reservedSeatIds, heldSeatIds and availableSeatCount", description = "Get show, screen, versioned layout, bookedSeatIds, reservedSeatIds, heldSeatIds and availableSeatCount. Seats reflect this show assigned layout.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successful request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.slokam.av.docs.ApiDocumentationModels.ShowAvailability.class)))
    @GetMapping("/{id}/availability")
    public Map<String, Object> availability(@PathVariable String id) {
        return service.availability(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create a show with valid movie, theatre and screen relationships", description = "Create a show with valid movie, theatre and screen relationships. Overlapping shows are rejected.")
    @PostMapping
    public Show create(@RequestBody Show show) {
        return service.create(show);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Update a show after validating scheduling and layout constraints", description = "Update a show after validating scheduling and layout constraints.")
    @PutMapping("/{id}")
    public Show update(@PathVariable String id, @RequestBody Show show) {
        return service.update(id, show);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Cancel a show", description = "Cancel a show.")
    @DeleteMapping("/{id}")
    public void cancel(@PathVariable String id) {
        service.cancel(id);
    }
}
