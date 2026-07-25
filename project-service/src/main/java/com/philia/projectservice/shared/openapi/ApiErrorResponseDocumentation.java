package com.philia.projectservice.shared.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(name = "ApiErrorResponse", description = "Standard failed API response envelope.")
public record ApiErrorResponseDocumentation(
        boolean success,
        String code,
        String message,
        Object data,
        Map<String, String> errors,
        Instant timestamp
) {
}
