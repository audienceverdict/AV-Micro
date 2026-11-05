package com.slokam.av.dto;

import jakarta.validation.constraints.*;

public record AdminCancellationRequest(
        @NotBlank @Size(max = 500) String reason, java.util.List<String> seatIds) {}
