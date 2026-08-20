package com.agentic.urlshortener.web;

import com.agentic.urlshortener.config.AppProperties;
import com.agentic.urlshortener.domain.ShortUrl;
import com.agentic.urlshortener.web.dto.ShortUrlResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ShortUrlMapper {

    private final AppProperties properties;

    public ShortUrlMapper(AppProperties properties) {
        this.properties = properties;
    }

    public ShortUrlResponse toResponse(ShortUrl shortUrl) {
        return new ShortUrlResponse(
                shortUrl.getShortCode(),
                properties.normalisedBaseUrl() + "/" + shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt(),
                shortUrl.getLastAccessedAt(),
                shortUrl.isActive(),
                shortUrl.isExpired(Instant.now()),
                shortUrl.getTotalClicks(),
                shortUrl.getCreatedBy());
    }
}
