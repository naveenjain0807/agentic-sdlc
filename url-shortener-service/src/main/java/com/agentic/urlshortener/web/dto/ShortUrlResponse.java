package com.agentic.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ShortUrlResponse", description = "A shortened link and its current state")
public record ShortUrlResponse(

        @Schema(example = "1Ha7Bd") String shortCode,
        @Schema(example = "http://localhost:8080/1Ha7Bd") String shortUrl,
        @Schema(example = "https://www.google.com") String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        Instant lastAccessedAt,
        boolean active,
        boolean expired,
        long totalClicks,
        @Schema(example = "naveen") String createdBy) {
}
