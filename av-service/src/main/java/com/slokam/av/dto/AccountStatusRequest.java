package com.slokam.av.dto;

import jakarta.validation.constraints.*;

public record AccountStatusRequest(@NotNull Boolean enabled) {}
