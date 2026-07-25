package com.philia.productservice.shared.web;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, String> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, Map.of(), Instant.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null, Map.of(), Instant.now());
    }

    public static ApiResponse<Void> validationFailure(Map<String, String> errors) {
        return new ApiResponse<>(
                false,
                "VALIDATION_FAILED",
                "One or more request fields are invalid.",
                null,
                Map.copyOf(errors),
                Instant.now()
        );
    }
}
