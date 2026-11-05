package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class PersonResponse {

    public String fullName;

    public String biography;

    public String profileImageUrl;

    public LocalDate dateOfBirth;

    public String nationality;

    public String officialWebsite;

    public Set<@NotBlank @Size(max = 100) String> skills;

    public String id;

    public Instant createdAt;

    public Instant updatedAt;

    public List<PersonSocialLinkResponse> socialLinks;
}
