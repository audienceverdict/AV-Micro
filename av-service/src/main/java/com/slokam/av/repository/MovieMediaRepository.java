package com.slokam.av.repository;
import com.slokam.av.entity.MovieMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface MovieMediaRepository extends JpaRepository<MovieMedia, String> {
    List<MovieMedia> findByMovieIdOrderByDisplayOrderAscIdAsc(String movieId);
    void deleteByMovieId(String movieId);
}
