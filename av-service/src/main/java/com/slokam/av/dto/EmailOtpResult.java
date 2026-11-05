package com.slokam.av.dto;

public record EmailOtpResult(
        boolean registrationRequired, UserResponse user, String accessToken, String tokenType) {}
