package com.agentic.urlshortener.web;

import com.agentic.urlshortener.web.dto.PingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight liveness check, separate from the Actuator health endpoint. */
@RestController
@RequestMapping("/api/v1/ping")
@Tag(name = "4. Ping", description = "Lightweight liveness check")
public class PingController {

    @Operation(summary = "Ping", description = "Returns the current server time to confirm the service is up.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service is up")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PingResponse ping() {
        return new PingResponse("ok", Instant.now());
    }
}
