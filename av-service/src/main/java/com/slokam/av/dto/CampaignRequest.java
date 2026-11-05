package com.slokam.av.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CampaignRequest(
        @NotNull Target target,
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 5000) String message,
        String showId,
        String movieId,
        String status,
        String sortBy,
        String sortDirection) {
    public enum Target {
        ALL_USERS,
        SHOW_BOOKERS,
        MOVIE_BOOKERS
    }
}
