package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.entity.Review;
import com.slokam.av.entity.ReviewStatus;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.MovieRepository;
import com.slokam.av.repository.ReviewRepository;
import com.slokam.av.client.IdentityClient;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.*;

@Service
public class ReviewService {
    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private final ReviewRepository reviews;
    private final MovieRepository movies;
    private final IdentityClient users;

    public ReviewService(ReviewRepository reviews, MovieRepository movies, IdentityClient users) {
        this.reviews = reviews;
        this.movies = movies;
        this.users = users;
    }

    public List<Review> list(String movieId, ReviewStatus status) {
        log.debug("Processing ReviewService.list");
        return movieId == null
                ? reviews.findByStatusOrderByCreatedAtDesc(status)
                : reviews.findByMovieIdAndStatusOrderByCreatedAtDesc(movieId, status);
    }

    @Transactional
    public Review create(Principal p, Review r) {
        log.debug("Processing ReviewService.create");
        movies.findById(r.movieId)
                .orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found"));
        var u = users.findById(p.getName()).orElseThrow();
        r.id = UUID.randomUUID().toString();
        r.userId = u.id;
        r.author = r.author == null || r.author.isBlank() ? u.name : r.author;
        r.status = ReviewStatus.PENDING;
        r.isHighlighted = false;
        validate(r);
        log.info("Creating review reviewId={} movieId={}", r.id, r.movieId);
        return reviews.save(r);
    }

    @Transactional
    public Review update(String id, Principal p, Review next) {
        log.debug("Processing ReviewService.update");
        var r =
                reviews.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "REVIEW_NOT_FOUND", "Review not found"));
        if (!Objects.equals(r.userId, p.getName()))
            throw new ApiException(403, "FORBIDDEN", "Access denied");
        r.title = next.title;
        r.text = next.text;
        r.rating = next.rating;
        r.videoUrl = next.videoUrl;
        r.platform = next.platform;
        r.thumbnail = next.thumbnail;
        r.status = ReviewStatus.PENDING;
        validate(r);
        log.info("Review updated reviewId={} status={}", r.id, r.status);
        return r;
    }

    @Transactional
    public Review moderate(String id, ReviewStatus status, boolean highlighted) {
        log.debug("Processing ReviewService.moderate");
        var r =
                reviews.findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                404, "REVIEW_NOT_FOUND", "Review not found"));
        r.status = status;
        r.isHighlighted = highlighted;
        log.info("Review updated reviewId={} status={}", r.id, r.status);
        return r;
    }

    private void validate(Review r) {
        if (r.rating < 1 || r.rating > 5)
            throw new ApiException(400, "INVALID_RATING", "Rating must be between 1 and 5");
    }
}
