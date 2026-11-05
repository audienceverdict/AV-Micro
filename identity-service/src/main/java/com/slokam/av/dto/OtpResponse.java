package com.slokam.av.dto;

import jakarta.validation.constraints.*;

public record OtpResponse(
        boolean success, String message, long expiresInSeconds, long resendAfterSeconds) {}
