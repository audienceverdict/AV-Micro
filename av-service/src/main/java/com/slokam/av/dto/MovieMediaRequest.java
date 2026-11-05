package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class MovieMediaRequest {
    @NotNull
    public MovieMediaType mediaType;
    @Size(max = 200)
    public String title;
    @Size(max = 2000)
    public String description;
    @NotBlank @HttpUrl @Size(max = 2000)
    public String mediaUrl;
    @HttpUrl @Size(max = 2000)
    public String thumbnailUrl;
    @Size(max = 80)
    public String language;

    public boolean isOfficial;

    public boolean isPrimary;
    @PositiveOrZero
    public Integer displayOrder;
    @Size(max = 80)
    public String sourcePlatform;

    public Instant publishedAt;
}
