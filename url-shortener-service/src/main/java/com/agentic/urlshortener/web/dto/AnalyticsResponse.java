package com.agentic.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "AnalyticsResponse", description = "Click analytics for one short link")
public record AnalyticsResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        Instant lastAccessedAt,
        boolean active,
        boolean expired,
        long totalClicks,
        long uniqueVisitors,
        List<CountEntry> clicksByDay,
        List<CountEntry> topReferers,
        List<CountEntry> clicksByDeviceType,
        List<ClickEntry> recentClicks) {
}
