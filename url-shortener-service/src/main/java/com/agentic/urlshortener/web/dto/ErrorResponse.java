package com.agentic.urlshortener.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(name = "ErrorResponse", description = "Standard error payload")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        @Schema(example = "Not Found") String error,
        @Schema(example = "No short URL found for code 'abc123'") String message,
        @Schema(example = "/api/v1/urls/abc123") String path,
        Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, Map.of());
    }
}
