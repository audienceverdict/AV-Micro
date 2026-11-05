package com.slokam.av.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyPaymentRequest {
    @NotBlank public String provider;
    @NotBlank public String providerReference;
}
