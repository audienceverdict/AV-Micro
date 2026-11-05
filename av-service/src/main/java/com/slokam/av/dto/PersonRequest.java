package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class PersonRequest {
    @NotBlank @Size(max = 150)
    public String fullName;
    @Size(max = 10000)
    public String biography;
    @HttpUrl @Size(max = 2000)
    public String profileImageUrl;
    @PastOrPresent
    public LocalDate dateOfBirth;
    @Size(max = 100)
    public String nationality;
    @HttpUrl @Size(max = 2000)
    public String officialWebsite;
    @Size(max = 100)
    public Set<@NotBlank @Size(max = 100) String> skills;
}
