package com.agentic.urlshortener.exception;

public class ShortUrlNotFoundException extends RuntimeException {

    private final String shortCode;

    public ShortUrlNotFoundException(String shortCode) {
        super("No short URL found for code '" + shortCode + "'");
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
