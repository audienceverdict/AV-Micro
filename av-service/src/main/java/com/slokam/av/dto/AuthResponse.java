package com.slokam.av.dto;

import jakarta.validation.constraints.*;

public record AuthResponse(UserResponse user, String accessToken, String tokenType) {}
