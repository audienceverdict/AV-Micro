package com.slokam.av.repository;

import com.slokam.av.entity.Review;
import com.slokam.av.entity.ReviewStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ReviewRepository extends JpaRepository<Review, String> {
    Optional<Review> findFirstByMovieIdAndUserIdOrderByCreatedAtDesc(String movieId, String userId);

    List<Review> findByMovieIdAndStatusOrderByCreatedAtDesc(String movieId, ReviewStatus status);

    List<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status);

    @Query("select r.rating from Review r where r.movieId = :movieId "
            + "and r.userId is not null and r.platform = :platform and r.status in :statuses and r.rating > 0")
    List<Integer> findPublicMovieRatings(@Param("movieId") String movieId,
            @Param("platform") String platform,
            @Param("statuses") Collection<ReviewStatus> statuses);
}
