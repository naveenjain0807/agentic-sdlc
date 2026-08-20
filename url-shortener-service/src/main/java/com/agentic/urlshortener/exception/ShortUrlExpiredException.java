package com.agentic.urlshortener.exception;

import java.time.Instant;

public class ShortUrlExpiredException extends RuntimeException {

    private final String shortCode;
    private final Instant expiredAt;

    public ShortUrlExpiredException(String shortCode, Instant expiredAt) {
        super("Short URL '" + shortCode + "' expired at " + expiredAt);
        this.shortCode = shortCode;
        this.expiredAt = expiredAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }
}
