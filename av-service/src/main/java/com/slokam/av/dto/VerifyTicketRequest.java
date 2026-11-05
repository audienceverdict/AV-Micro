package com.slokam.av.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyTicketRequest(
        @NotBlank @Size(max = 256) String qrPayload,
        @NotBlank @Size(max = 36) String showId) {}
