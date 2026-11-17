package com.slokam.av.repository;

import com.slokam.av.entity.MovieInterest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieInterestRepository extends JpaRepository<MovieInterest, String> {
    long countByMovieId(String movieId);
    boolean existsByMovieIdAndUserId(String movieId, String userId);
    void deleteByMovieIdAndUserId(String movieId, String userId);
}
