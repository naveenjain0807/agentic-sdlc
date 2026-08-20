package com.agentic.urlshortener.web;

import com.agentic.urlshortener.domain.ShortUrl;
import com.agentic.urlshortener.service.ClickTrackingService;
import com.agentic.urlshortener.service.UrlShortenerService;
import com.agentic.urlshortener.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public redirect endpoint.
 *
 * <p>The path variable is constrained by regex so it cannot shadow
 * {@code /api/**}, {@code /actuator/**}, {@code /swagger-ui/**} or
 * {@code /v3/api-docs}; literal mappings also win over pattern mappings in
 * Spring's route ranking.
 */
@RestController
@Tag(name = "2. Redirect", description = "Follow a short link")
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final UrlShortenerService urlShortenerService;
    private final ClickTrackingService clickTrackingService;

    public RedirectController(UrlShortenerService urlShortenerService, ClickTrackingService clickTrackingService) {
        this.urlShortenerService = urlShortenerService;
        this.clickTrackingService = clickTrackingService;
    }

    @Operation(
            summary = "Follow a short link",
            description = """
                    Records a click and issues a `302 Found` to the original URL.

                    Swagger's "Try it out" follows redirects automatically, so the response you
                    see may be the target page. Use `curl -i http://localhost:8080/{shortCode}`
                    to observe the raw 302 and its `Location` header.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to the original URL"),
            @ApiResponse(responseCode = "404", description = "Unknown or deleted code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Link has expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{shortCode:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "The short code", example = "welcome") @PathVariable String shortCode,
            HttpServletRequest request) {

        ShortUrl shortUrl = urlShortenerService.resolveForRedirect(shortCode);
        String target = shortUrl.getOriginalUrl();
        try {
            clickTrackingService.record(shortUrl, request);
        } catch (RuntimeException ex) {
            log.warn("Failed to record click for {}: {}", shortCode, ex.toString());
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .build();
    }
}
