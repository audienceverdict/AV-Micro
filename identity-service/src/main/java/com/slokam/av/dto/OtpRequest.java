package com.slokam.av.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record OtpRequest(@NotBlank @Size(max=255) String destination) {}
