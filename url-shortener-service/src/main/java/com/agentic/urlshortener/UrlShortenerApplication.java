package com.agentic.urlshortener;

import com.agentic.urlshortener.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the URL shortener service.
 *
 * <p>This module is the "target system" built by the agentic SDLC orchestrator.
 * It is intentionally self-contained: H2 in-memory storage, Flyway-managed
 * schema, and an OpenAPI/Swagger surface so it can be exercised end-to-end
 * with nothing but {@code docker compose up}.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
