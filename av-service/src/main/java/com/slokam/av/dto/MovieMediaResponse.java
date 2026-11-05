package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class MovieMediaResponse {

    public MovieMediaType mediaType;

    public String title;

    public String description;

    public String mediaUrl;

    public String thumbnailUrl;

    public String language;

    public boolean isOfficial;

    public boolean isPrimary;

    public int displayOrder;

    public String sourcePlatform;

    public Instant publishedAt;

    public String id;

    public String movieId;

    public String embedUrl;

    public Instant createdAt;

    public Instant updatedAt;
}
