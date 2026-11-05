package com.slokam.av.repository;
import com.slokam.av.entity.MovieCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface MovieCreditRepository extends JpaRepository<MovieCredit, String> {
    List<MovieCredit> findByMovieIdOrderByBillingOrderAscIdAsc(String movieId);
    List<MovieCredit> findAllByPersonId(String personId);
    Page<MovieCredit> findByPersonId(String personId, Pageable pageable);
    boolean existsByMovieIdAndPersonIdAndRoleKeyAndIdNot(String movieId, String personId, String roleKey, String id);
    void deleteByMovieId(String movieId);
}
