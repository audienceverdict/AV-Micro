package com.slokam.av.repository;

import com.slokam.av.entity.Movie;
import com.slokam.av.entity.MovieStatus;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Movie> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Movie m where m.id = :id")
    java.util.Optional<Movie> lockById(@org.springframework.data.repository.query.Param("id") String id);
    Page<Movie> findByStatus(MovieStatus status, Pageable pageable);
}
