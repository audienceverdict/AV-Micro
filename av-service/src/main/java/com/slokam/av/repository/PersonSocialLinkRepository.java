package com.slokam.av.repository;
import com.slokam.av.entity.PersonSocialLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface PersonSocialLinkRepository extends JpaRepository<PersonSocialLink, String> {
    List<PersonSocialLink> findByPersonIdOrderByIdAsc(String personId);
    List<PersonSocialLink> findByPersonIdIn(Collection<String> personIds);
}
