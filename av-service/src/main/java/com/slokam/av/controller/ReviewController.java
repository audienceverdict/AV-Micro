package com.slokam.av.controller;

import com.slokam.av.entity.Review;
import com.slokam.av.entity.ReviewStatus;
import com.slokam.av.service.ReviewService;

import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Review")
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List reviews, optionally filtered by movieId", description = "List reviews, optionally filtered by movieId. status defaults to APPROVED.")
    @GetMapping
    public List<Review> list(
            @RequestParam(required = false) String movieId,
            @RequestParam(defaultValue = "APPROVED") ReviewStatus status) {
        return service.list(movieId, status);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create a movie review for the authenticated account", description = "Create a movie review for the authenticated account.")
    @PostMapping
    public Review create(Principal p, @RequestBody Review review) {
        return service.create(p, review);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Update a review owned by the authenticated account", description = "Update a review owned by the authenticated account.")
    @PutMapping("/{id}")
    public Review update(@PathVariable String id, Principal p, @RequestBody Review review) {
        return service.update(id, p, review);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Set review moderation status and optional highlighted flag", description = "Set review moderation status and optional highlighted flag.")
    @PostMapping("/admin/{id}/moderate")
    public Review moderate(
            @PathVariable String id,
            @RequestParam ReviewStatus status,
            @RequestParam(defaultValue = "false") boolean highlighted) {
        return service.moderate(id, status, highlighted);
    }
}
