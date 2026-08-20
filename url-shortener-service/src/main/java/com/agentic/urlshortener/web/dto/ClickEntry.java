package com.agentic.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ClickEntry", description = "A single recorded redirect")
public record ClickEntry(Instant clickedAt,
                         @Schema(example = "127.0.0.1") String ipAddress,
                         @Schema(example = "DESKTOP") String deviceType,
                         String referer,
                         String userAgent) {
}
