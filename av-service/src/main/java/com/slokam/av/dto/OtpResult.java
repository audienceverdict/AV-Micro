package com.slokam.av.dto;
public record OtpResult(boolean registrationRequired, String registrationToken,
        UserResponse user, String accessToken, String tokenType) {}
