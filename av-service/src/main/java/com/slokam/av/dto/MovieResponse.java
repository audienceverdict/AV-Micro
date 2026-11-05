package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class MovieResponse {

    public String title;

    public String posterUrl;

    public List<@NotBlank @HttpUrl @Size(max = 2000) String> posterImages;

    public String backdropUrl;

    public String trailerUrl;

    public String description;

    public List<@NotBlank @Size(max = 80) String> genre;

    public String language;

    public Integer duration;

    public LocalDate releaseDate;

    public String certification;

    public String director;

    public List<@NotBlank @Size(max = 150) String> cast;

    public String production;

    public MovieStatus status;

    public String originalTitle;

    public String tagline;

    public String originalLanguage;

    public List<@NotBlank @Size(max = 80) String> languages;

    public ReleaseStatus releaseStatus;

    public ProductionStatus productionStatus;

    public String countryOfOrigin;

    public List<@NotBlank @Size(max = 200) String> filmingLocations;

    public List<@NotBlank @Size(max = 150) String> productionCompanies;

    public String distributor;

    public String officialWebsite;

    public LocalDate announcementDate;

    public LocalDate productionStartDate;

    public LocalDate productionEndDate;

    public BigDecimal budget;

    public String currencyCode;

    public String id;

    public Integer durationMinutes;

    public String synopsis;

    public String externalTrailerUrl;

    public List<String> genres;

    public Instant createdAt;

    public Instant updatedAt;

    public List<MovieMediaResponse> media;

    public List<MovieCreditResponse> castCredits;

    public Map<String, List<MovieCreditResponse>> crewByDepartment;
}
