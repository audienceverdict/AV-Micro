package com.slokam.av.repository;

import com.slokam.av.entity.Screen;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface ScreenRepository extends JpaRepository<Screen, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Screen s where s.id=:id")
    Optional<Screen> lockById(@org.springframework.data.repository.query.Param("id") String id);

    List<Screen> findByTheatreId(String theatreId);
}
