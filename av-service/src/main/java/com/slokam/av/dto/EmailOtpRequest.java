package com.slokam.av.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailOtpRequest(@NotBlank @Email String email) {}
