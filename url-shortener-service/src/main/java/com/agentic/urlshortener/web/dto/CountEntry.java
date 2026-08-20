package com.agentic.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CountEntry", description = "A label and how many clicks it accounts for")
public record CountEntry(@Schema(example = "https://news.ycombinator.com/") String label,
                         @Schema(example = "42") long count) {
}
