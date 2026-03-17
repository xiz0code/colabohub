package com.colaborapp.common.exception;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> validationErrors) {

    public static ApiErrorResponse of(int status, String error, String message) {
        return new ApiErrorResponse(Instant.now(), status, error, message, Map.of());
    }

    public static ApiErrorResponse ofValidation(int status, String error, String message, Map<String, String> validationErrors) {
        return new ApiErrorResponse(Instant.now(), status, error, message, validationErrors);
    }
}
