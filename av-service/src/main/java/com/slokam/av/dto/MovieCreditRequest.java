package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class MovieCreditRequest {
    @NotBlank @Size(max = 36)
    public String personId;
    @NotNull
    public CreditType creditType;
    @Size(max = 100)
    public String department;
    @NotBlank @Size(max = 150)
    public String roleTitle;
    @Size(max = 150)
    public String characterName;
    @Size(max = 2000)
    public String characterDescription;
    @HttpUrl @Size(max = 2000)
    public String characterImageUrl;

    public CharacterCategory characterCategory;
    @Size(max = 150)
    public String characterOccupation;

    public Map<@NotBlank @Size(max = 100) String, @NotBlank @Size(max = 500) String> characterRelationships;
    @PositiveOrZero
    public Integer screenTimeMinutes;
    @PositiveOrZero
    public Integer billingOrder;

    public boolean isMainCast;

    public boolean isCameo;

    public boolean isVoiceRole;
}
