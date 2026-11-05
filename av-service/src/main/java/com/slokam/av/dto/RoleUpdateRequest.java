package com.slokam.av.dto;

import jakarta.validation.constraints.*;

public record RoleUpdateRequest(@NotNull com.slokam.av.entity.Role role) {}
