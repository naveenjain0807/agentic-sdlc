package com.agentic.urlshortener.web;

import com.agentic.urlshortener.domain.ShortUrl;
import com.agentic.urlshortener.service.UrlShortenerService;
import com.agentic.urlshortener.web.dto.CreateShortUrlRequest;
import com.agentic.urlshortener.web.dto.ErrorResponse;
import com.agentic.urlshortener.web.dto.PageResponse;
import com.agentic.urlshortener.web.dto.ShortUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "1. Short links", description = "Create, inspect and retire short links")
public class UrlController {

    private static final int MAX_PAGE_SIZE = 200;

    private final UrlShortenerService urlShortenerService;
    private final ShortUrlMapper mapper;

    public UrlController(UrlShortenerService urlShortenerService, ShortUrlMapper mapper) {
        this.urlShortenerService = urlShortenerService;
        this.mapper = mapper;
    }

    @Operation(
            summary = "Create a short link",
            description = """
                    Shortens an absolute http(s) URL.

                    * Repeating the same URL (no alias, no expiry) returns the **existing** link with `200 OK`.
                    * Send an `Idempotency-Key` header to make retries of any request safely repeatable.
                    * A brand new link is returned with `201 Created` and a `Location` header.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short link created"),
            @ApiResponse(responseCode = "200", description = "Existing short link re-used"),
            @ApiResponse(responseCode = "400", description = "Invalid URL or expiry",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Custom alias already taken",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/api/v1/shorten",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShortUrlResponse> shorten(
            @Valid @RequestBody CreateShortUrlRequest request,
            @Parameter(description = "Optional key making the create call safely retryable")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        UrlShortenerService.CreateResult result = urlShortenerService.create(request, idempotencyKey);
        ShortUrlResponse body = mapper.toResponse(result.shortUrl());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header(HttpHeaders.LOCATION, body.shortUrl())
                .body(body);
    }

    @Operation(summary = "List short links", description = "Newest first. Use `activeOnly=false` to include deleted links.")
    @GetMapping(value = "/api/v1/urls", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<ShortUrlResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));

        Page<ShortUrl> result = urlShortenerService.list(pageable, activeOnly);
        List<ShortUrlResponse> content = result.getContent().stream().map(mapper::toResponse).toList();
        return PageResponse.of(result, content);
    }

    @Operation(summary = "Get one short link", description = "Metadata for a code, including its click counter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/api/v1/urls/{shortCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ShortUrlResponse get(@PathVariable String shortCode) {
        return mapper.toResponse(urlShortenerService.findByCode(shortCode));
    }

    @Operation(summary = "Delete a short link",
            description = "Soft delete: the code stops redirecting but its click history is kept for analytics.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "Unknown code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode) {
        urlShortenerService.deactivate(shortCode);
        return ResponseEntity.noContent().build();
    }
}
