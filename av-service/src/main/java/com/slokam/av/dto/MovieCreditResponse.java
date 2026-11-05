package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class MovieCreditResponse {

    public String personId;

    public CreditType creditType;

    public String department;

    public String roleTitle;

    public String characterName;

    public String characterDescription;

    public String characterImageUrl;

    public CharacterCategory characterCategory;

    public String characterOccupation;

    public Map<@NotBlank @Size(max = 100) String, @NotBlank @Size(max = 500) String> characterRelationships;

    public Integer screenTimeMinutes;

    public Integer billingOrder;

    public boolean isMainCast;

    public boolean isCameo;

    public boolean isVoiceRole;

    public String id;

    public String movieId;

    public PersonResponse person;

    public Instant createdAt;

    public Instant updatedAt;
}
