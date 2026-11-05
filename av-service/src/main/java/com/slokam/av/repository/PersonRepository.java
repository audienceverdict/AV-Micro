package com.slokam.av.repository;
import com.slokam.av.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface PersonRepository extends JpaRepository<Person, String> {
    Page<Person> findByFullNameContainingIgnoreCase(String name, Pageable pageable);
}
