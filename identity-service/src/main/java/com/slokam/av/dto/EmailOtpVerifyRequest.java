package com.slokam.av.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailOtpVerifyRequest(
        @NotBlank @Email String email, @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {}
