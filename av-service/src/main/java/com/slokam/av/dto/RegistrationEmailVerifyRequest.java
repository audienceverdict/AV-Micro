package com.slokam.av.dto;
import jakarta.validation.constraints.*;
public record RegistrationEmailVerifyRequest(@NotBlank @Size(max=4096) String registrationToken, @NotBlank @Email @Size(max=255) String email, @NotBlank @Pattern(regexp="[0-9]{6}") String otp) {}
