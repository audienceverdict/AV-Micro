package com.slokam.av.controller;

import com.slokam.av.entity.LayoutSeat;
import com.slokam.av.entity.LayoutVersion;
import com.slokam.av.entity.Screen;
import com.slokam.av.entity.Theatre;
import com.slokam.av.service.TheatreService;

import jakarta.validation.Valid;

import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Theatre")
@RestController
@RequestMapping("/api/v1/theatres")
public class TheatreController {
    private final TheatreService service;
    private final com.slokam.av.service.LayoutCompatibilityService compatibility;

    public TheatreController(TheatreService service, com.slokam.av.service.LayoutCompatibilityService compatibility) {
        this.service = service;
        this.compatibility = compatibility;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Assess legacy show layouts against the screen layout", description = "Assess legacy show layouts against the screen layout. adoptCurrentLegacyLayout explicitly allows adoption of the current legacy layout.")
    @GetMapping("/screens/{screenId}/layout-compatibility")
    public List<com.slokam.av.service.LayoutCompatibilityService.Assessment> compatibility(
            @PathVariable String screenId,
            @RequestParam(defaultValue = "false") boolean adoptCurrentLegacyLayout) {
        return compatibility.assess(screenId, adoptCurrentLegacyLayout);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Reconcile compatible legacy show layouts", description = "Reconcile compatible legacy show layouts. Incompatible layouts remain blocked; inspect each returned assessment.")
    @PostMapping("/screens/{screenId}/layout-compatibility/reconcile")
    public List<com.slokam.av.service.LayoutCompatibilityService.Assessment> reconcile(
            @PathVariable String screenId,
            @RequestParam(defaultValue = "false") boolean adoptCurrentLegacyLayout) {
        return compatibility.reconcile(screenId, adoptCurrentLegacyLayout);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List theatres with zero-based pagination", description = "List theatres with zero-based pagination.")
    @GetMapping
    public Page<Theatre> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get a theatre by ID", description = "Get a theatre by ID.")
    @GetMapping("/{id}")
    public Theatre get(@PathVariable String id) {
        return service.get(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create a theatre", description = "Create a theatre.")
    @PostMapping
    public Theatre create(@Valid @RequestBody Theatre theatre) {
        return service.create(theatre);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Update a theatre by ID", description = "Update a theatre by ID.")
    @PutMapping("/{id}")
    public Theatre update(@PathVariable String id, @Valid @RequestBody Theatre theatre) {
        return service.update(id, theatre);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Delete a theatre subject to existing show and booking constraints", description = "Delete a theatre subject to existing show and booking constraints.")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List screens and seating layouts for a theatre", description = "List screens and seating layouts for a theatre.")
    @GetMapping("/{id}/screens")
    public List<Screen> screens(@PathVariable String id) {
        return service.screens(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create a screen belonging to the requested theatre", description = "Create a screen belonging to the requested theatre.")
    @PostMapping("/{id}/screens")
    public Screen createScreen(@PathVariable String id, @RequestBody Screen screen) {
        return service.createScreen(id, screen);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Update a screen and its layout settings", description = "Update a screen and its layout settings.")
    @PutMapping("/screens/{screenId}")
    public Screen updateScreen(@PathVariable String screenId, @RequestBody Screen screen) {
        return service.updateScreen(screenId, screen);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Delete a screen subject to related show and booking constraints", description = "Delete a screen subject to related show and booking constraints.")
    @DeleteMapping("/screens/{screenId}")
    public void deleteScreen(@PathVariable String screenId) {
        service.deleteScreen(screenId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List screen layout versions, newest first", description = "List screen layout versions, newest first.")
    @GetMapping("/screens/{screenId}/layout-versions")
    public List<LayoutVersion> layoutVersions(@PathVariable String screenId) {
        return service.layoutVersions(screenId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get a layout version and its seats", description = "Get a layout version and its seats.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successful request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.slokam.av.docs.ApiDocumentationModels.LayoutDetails.class)))
    @GetMapping("/screens/{screenId}/layout-versions/{versionId}")
    public Map<String, Object> layoutDetails(
            @PathVariable String screenId, @PathVariable String versionId) {
        return service.layoutDetails(screenId, versionId);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create an editable draft layout; optionally copy seats from sourceVersionId", description = "Create an editable draft layout; optionally copy seats from sourceVersionId.")
    @PostMapping("/screens/{screenId}/layout-versions")
    public LayoutVersion createLayoutVersion(
            @PathVariable String screenId,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(required = false) String sourceVersionId) {
        return service.createLayoutVersion(screenId, sourceVersionId, name);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Replace draft layout seats", description = "Replace draft layout seats. Each seat specifies its position, label and category.")
    @PutMapping("/screens/{screenId}/layout-versions/{versionId}/seats")
    public List<LayoutSeat> saveLayoutSeats(
            @PathVariable String screenId,
            @PathVariable String versionId,
            @RequestBody List<LayoutSeat> seats) {
        return service.saveLayoutSeats(screenId, versionId, seats);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Publish a valid draft layout and archive the previous active version", description = "Publish a valid draft layout and archive the previous active version. Existing shows retain their assigned layout.")
    @PostMapping("/screens/{screenId}/layout-versions/{versionId}/publish")
    public LayoutVersion publishLayoutVersion(
            @PathVariable String screenId, @PathVariable String versionId) {
        return service.publishLayoutVersion(screenId, versionId);
    }
}
