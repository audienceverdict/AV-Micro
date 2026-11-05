package com.slokam.av.exception;

import java.time.Instant;
import java.util.Map;

public record ApiErrors(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
    public static ApiErrors of(int status, String code, String message, String path) {
        return new ApiErrors(Instant.now(), status, code, message, path, Map.of());
    }
}
