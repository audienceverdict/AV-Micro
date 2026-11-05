package com.slokam.av.dto;

import com.slokam.av.entity.Role;
import jakarta.validation.constraints.*;
import java.util.Set;

/** Replaces additional roles and verifier assignments, preserving the primary role. */
public record AdditionalRolesRequest(
        @NotNull @Size(max = 3) Set<@NotNull Role> roles,
        @NotNull @Size(max = 500) Set<@NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{1,36}") String> verifierShowIds) {}
