package com.agentic.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "PingResponse", description = "Lightweight liveness check result")
public record PingResponse(

        @Schema(example = "ok") String status,
        Instant timestamp) {
}
