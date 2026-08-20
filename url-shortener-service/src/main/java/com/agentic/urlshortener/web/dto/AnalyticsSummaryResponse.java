package com.agentic.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "AnalyticsSummaryResponse", description = "Service-wide analytics roll-up")
public record AnalyticsSummaryResponse(
        Instant generatedAt,
        long totalUrls,
        long activeUrls,
        long inactiveUrls,
        long expiredUrls,
        long totalClicks,
        List<ShortUrlResponse> topLinks) {
}
