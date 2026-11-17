package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.entity.Review;
import com.slokam.av.entity.ReviewStatus;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.MovieRepository;
import com.slokam.av.repository.ReviewRepository;
import com.slokam.av.repository.BookingRepository;
import com.slokam.av.entity.BookingStatus;
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
    private final BookingRepository bookings;

    public ReviewService(ReviewRepository reviews, MovieRepository movies, IdentityClient users,
            BookingRepository bookings) {
        this.reviews = reviews;
        this.movies = movies;
        this.users = users;
        this.bookings = bookings;
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
        if (r.movieId == null || r.movieId.isBlank())
            throw new ApiException(400, "MOVIE_REQUIRED", "A movie is required for a review");
        movies.lockById(r.movieId)
                .orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found"));
        var u = users.findById(p.getName()).orElseThrow();
        if (reviews.findFirstByMovieIdAndUserIdOrderByCreatedAtDesc(r.movieId, u.id).isPresent())
            throw new ApiException(409, "REVIEW_ALREADY_SUBMITTED", "You have already used your review for this movie");
        if (!bookings.hasAttendedMovie(u.id, r.movieId, BookingStatus.CONFIRMED))
            throw new ApiException(403, "MOVIE_NOT_ATTENDED", "A confirmed ticket marked as attended is required before reviewing this movie");
        r.id = UUID.randomUUID().toString();
        r.userId = u.id;
        r.author = r.author == null || r.author.isBlank() ? u.name : r.author;
        r.title = "Audience review";
        r.platform = "OTHER";
        r.videoUrl = null;
        r.thumbnail = null;
        r.status = ReviewStatus.PENDING;
        r.isHighlighted = false;
        validate(r);
        log.info("Creating review reviewId={} movieId={}", r.id, r.movieId);
        return reviews.save(r);
    }

    public ReviewEligibility eligibility(Principal p, String movieId) {
        movies.findById(movieId).orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found"));
        var user = users.findById(p.getName()).orElseThrow();
        var existing = reviews.findFirstByMovieIdAndUserIdOrderByCreatedAtDesc(movieId, user.id);
        if (existing.isPresent()) return new ReviewEligibility(false, true, existing.get(), "You have already submitted your one review for this movie.");
        boolean attended = bookings.hasAttendedMovie(user.id, movieId, BookingStatus.CONFIRMED);
        return new ReviewEligibility(attended, false, null, attended ? "Share your review of the movie you attended." : "A confirmed ticket marked as attended is required before you can review this movie.");
    }

    public PublicRatingSummary ratingSummary(String movieId) {
        movies.findById(movieId).orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found"));
        var scores = reviews.findPublicMovieRatings(movieId, "OTHER", List.of(ReviewStatus.PENDING, ReviewStatus.APPROVED));
        var distribution = new LinkedHashMap<Integer, Long>();
        for (int star = 1; star <= 5; star++) distribution.put(star, 0L);
        long total = 0;
        for (int score : scores) {
            if (score < 1 || score > 5) continue;
            total += score;
            distribution.compute(score, (key, count) -> count + 1);
        }
        long count = distribution.values().stream().mapToLong(Long::longValue).sum();
        return new PublicRatingSummary(count == 0 ? 0 : (double) total / count, count, distribution);
    }

    public record ReviewEligibility(boolean eligible, boolean alreadyReviewed, Review review, String message) {}
    public record PublicRatingSummary(double average, long count, Map<Integer, Long> distribution) {}

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
        if (r.text == null || r.text.isBlank() || r.text.length() > 2000)
            throw new ApiException(400, "INVALID_REVIEW_TEXT", "Write a review of up to 2,000 characters");
    }
}
