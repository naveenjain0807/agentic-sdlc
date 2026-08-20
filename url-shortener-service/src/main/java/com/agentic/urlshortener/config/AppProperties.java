package com.agentic.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level tuning knobs, bound from the {@code app.*} namespace.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Public base URL used when rendering short links back to the caller. */
    private String baseUrl = "http://localhost:8080";

    /** Maximum accepted length of a long URL. */
    private int maxUrlLength = 2048;

    /** Default time-to-live in seconds. Zero (default) means "never expires". */
    private long defaultTtlSeconds = 0L;

    /** Number of most recent click events returned by the analytics endpoint. */
    private int recentClickLimit = 20;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getMaxUrlLength() {
        return maxUrlLength;
    }

    public void setMaxUrlLength(int maxUrlLength) {
        this.maxUrlLength = maxUrlLength;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(long defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public int getRecentClickLimit() {
        return recentClickLimit;
    }

    public void setRecentClickLimit(int recentClickLimit) {
        this.recentClickLimit = recentClickLimit;
    }

    /** Normalised base URL without a trailing slash. */
    public String normalisedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
