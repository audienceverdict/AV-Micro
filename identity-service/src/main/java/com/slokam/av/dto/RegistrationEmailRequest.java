package com.slokam.av.dto;
import jakarta.validation.constraints.*;
public record RegistrationEmailRequest(@NotBlank @Size(max=4096) String registrationToken, @NotBlank @Email @Size(max=255) String email) {}
