package com.agentic.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(name = "CreateShortUrlRequest", description = "Payload for creating a short link")
public record CreateShortUrlRequest(

        @Schema(description = "Absolute http(s) URL to shorten",
                example = "https://www.google.com/search?q=spring+boot",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        @Schema(description = "Optional vanity code. 3-32 chars of [A-Za-z0-9_-]", example = "my-link")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "customAlias must be 3-32 characters of A-Z, a-z, 0-9, _ or -")
        String customAlias,

        @Schema(description = "Optional time-to-live in seconds. Mutually exclusive with expiresAt.", example = "3600")
        @Min(value = 1, message = "ttlSeconds must be positive")
        Long ttlSeconds,

        @Schema(description = "Optional absolute expiry instant (ISO-8601). Mutually exclusive with ttlSeconds.",
                example = "2026-12-31T23:59:59Z")
        Instant expiresAt,

        @Schema(description = "Free-form owner/creator tag recorded for audit purposes", example = "naveen")
        @Size(max = 128)
        String createdBy) {
}
