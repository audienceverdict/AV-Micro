package com.slokam.av.dto;
import jakarta.validation.constraints.*;
public record OtpVerifyRequest(@NotBlank @Size(max=255) String destination,
        @NotBlank @Pattern(regexp="[0-9]{6}") String otp) {}
