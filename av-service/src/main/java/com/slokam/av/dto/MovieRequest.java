package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"id", "createdAt", "updatedAt", "media", "castCredits", "crewByDepartment"})
public class MovieRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final Set<String> suppliedFields = new HashSet<>();
    public boolean supplied(String name) { return suppliedFields.contains(name); }

    @NotBlank @Size(max = 200)
    public String title;
    @HttpUrl @Size(max = 2000)
    public String posterUrl;
    @Size(max = 100)
    public List<@NotBlank @HttpUrl @Size(max = 2000) String> posterImages;
    @HttpUrl @Size(max = 2000)
    public String backdropUrl;
    @HttpUrl @Size(max = 2000)
    public String trailerUrl;
    @Size(max = 2000)
    public String description;
    @Size(max = 100)
    public List<@NotBlank @Size(max = 80) String> genre;
    @Size(max = 80)
    public String language;
    @Positive
    public Integer duration;

    public LocalDate releaseDate;
    @Size(max = 30)
    public String certification;
    @Size(max = 150)
    public String director;
    @Size(max = 300)
    public List<@NotBlank @Size(max = 150) String> cast;
    @Size(max = 150)
    public String production;

    public MovieStatus status;
    @Size(max = 200)
    public String originalTitle;
    @Size(max = 300)
    public String tagline;
    @Size(max = 80)
    public String originalLanguage;
    @Size(max = 100)
    public List<@NotBlank @Size(max = 80) String> languages;

    public ReleaseStatus releaseStatus;

    public ProductionStatus productionStatus;
    @Size(max = 100)
    public String countryOfOrigin;
    @Size(max = 100)
    public List<@NotBlank @Size(max = 200) String> filmingLocations;
    @Size(max = 100)
    public List<@NotBlank @Size(max = 150) String> productionCompanies;
    @Size(max = 150)
    public String distributor;
    @HttpUrl @Size(max = 2000)
    public String officialWebsite;

    public LocalDate announcementDate;

    public LocalDate productionStartDate;

    public LocalDate productionEndDate;
    @DecimalMin("0.0") @Digits(integer = 17, fraction = 2)
    public BigDecimal budget;
    @Pattern(regexp = "[A-Z]{3}")
    public String currencyCode;
    @com.fasterxml.jackson.annotation.JsonAlias("durationMinutes")
    public void setDuration(Integer value) { suppliedFields.add("duration"); if (duration != null && !duration.equals(value)) throw new IllegalArgumentException("Conflicting duration aliases"); duration = value; }
    @com.fasterxml.jackson.annotation.JsonAlias("synopsis")
    public void setDescription(String value) { suppliedFields.add("description"); if (description != null && !description.equals(value)) throw new IllegalArgumentException("Conflicting description aliases"); description = value; }
    @com.fasterxml.jackson.annotation.JsonAlias("externalTrailerUrl")
    public void setTrailerUrl(String value) { suppliedFields.add("trailerUrl"); if (trailerUrl != null && !trailerUrl.equals(value)) throw new IllegalArgumentException("Conflicting trailerUrl aliases"); trailerUrl = value; }
    @com.fasterxml.jackson.annotation.JsonAlias("genres")
    public void setGenre(List<String> value) { suppliedFields.add("genre"); if (genre != null && !genre.equals(value)) throw new IllegalArgumentException("Conflicting genre aliases"); genre = value; }
    public void setTitle(String value) { suppliedFields.add("title"); title = value; }
    public void setPosterUrl(String value) { suppliedFields.add("posterUrl"); posterUrl = value; }
    public void setPosterImages(List<@NotBlank @HttpUrl @Size(max = 2000) String> value) { suppliedFields.add("posterImages"); posterImages = value; }
    public void setBackdropUrl(String value) { suppliedFields.add("backdropUrl"); backdropUrl = value; }
    public void setLanguage(String value) { suppliedFields.add("language"); language = value; }
    public void setReleaseDate(LocalDate value) { suppliedFields.add("releaseDate"); releaseDate = value; }
    public void setCertification(String value) { suppliedFields.add("certification"); certification = value; }
    public void setDirector(String value) { suppliedFields.add("director"); director = value; }
    public void setCast(List<@NotBlank @Size(max = 150) String> value) { suppliedFields.add("cast"); cast = value; }
    public void setProduction(String value) { suppliedFields.add("production"); production = value; }
    public void setStatus(MovieStatus value) { suppliedFields.add("status"); status = value; }
    public void setOriginalTitle(String value) { suppliedFields.add("originalTitle"); originalTitle = value; }
    public void setTagline(String value) { suppliedFields.add("tagline"); tagline = value; }
    public void setOriginalLanguage(String value) { suppliedFields.add("originalLanguage"); originalLanguage = value; }
    public void setLanguages(List<@NotBlank @Size(max = 80) String> value) { suppliedFields.add("languages"); languages = value; }
    public void setReleaseStatus(ReleaseStatus value) { suppliedFields.add("releaseStatus"); releaseStatus = value; }
    public void setProductionStatus(ProductionStatus value) { suppliedFields.add("productionStatus"); productionStatus = value; }
    public void setCountryOfOrigin(String value) { suppliedFields.add("countryOfOrigin"); countryOfOrigin = value; }
    public void setFilmingLocations(List<@NotBlank @Size(max = 200) String> value) { suppliedFields.add("filmingLocations"); filmingLocations = value; }
    public void setProductionCompanies(List<@NotBlank @Size(max = 150) String> value) { suppliedFields.add("productionCompanies"); productionCompanies = value; }
    public void setDistributor(String value) { suppliedFields.add("distributor"); distributor = value; }
    public void setOfficialWebsite(String value) { suppliedFields.add("officialWebsite"); officialWebsite = value; }
    public void setAnnouncementDate(LocalDate value) { suppliedFields.add("announcementDate"); announcementDate = value; }
    public void setProductionStartDate(LocalDate value) { suppliedFields.add("productionStartDate"); productionStartDate = value; }
    public void setProductionEndDate(LocalDate value) { suppliedFields.add("productionEndDate"); productionEndDate = value; }
    public void setBudget(BigDecimal value) { suppliedFields.add("budget"); budget = value; }
    public void setCurrencyCode(String value) { suppliedFields.add("currencyCode"); currencyCode = value; }
}
