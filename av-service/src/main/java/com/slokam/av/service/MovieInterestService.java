package com.slokam.av.service;

import com.slokam.av.entity.MovieInterest;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.MovieInterestRepository;
import com.slokam.av.repository.MovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Service
public class MovieInterestService {
    private final MovieInterestRepository interests;
    private final MovieRepository movies;

    public MovieInterestService(MovieInterestRepository interests, MovieRepository movies) {
        this.interests = interests;
        this.movies = movies;
    }

    @Transactional(readOnly = true)
    public InterestState get(String movieId, Principal principal) {
        movies.findById(movieId).orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found"));
        return state(movieId, principal != null && interests.existsByMovieIdAndUserId(movieId, principal.getName()));
    }

    @Transactional
    public InterestState add(String movieId, Principal principal) {
        movies.lockById(movieId).orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found"));
        if (!interests.existsByMovieIdAndUserId(movieId, principal.getName())) {
            var interest = new MovieInterest();
            interest.movieId = movieId;
            interest.userId = principal.getName();
            interests.save(interest);
        }
        return state(movieId, true);
    }

    @Transactional
    public InterestState remove(String movieId, Principal principal) {
        movies.lockById(movieId).orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found"));
        interests.deleteByMovieIdAndUserId(movieId, principal.getName());
        return state(movieId, false);
    }

    private InterestState state(String movieId, boolean interested) {
        return new InterestState(interests.countByMovieId(movieId), interested);
    }

    public record InterestState(long count, boolean interested) {}
}
