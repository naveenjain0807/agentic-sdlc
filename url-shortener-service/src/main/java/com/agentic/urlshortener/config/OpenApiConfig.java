package com.agentic.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlShortenerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("URL Shortener API")
                        .version("1.0.0")
                        .description("""
                                Create short links, follow them, and inspect click analytics.

                                **Quick start**
                                1. `POST /api/v1/shorten` with `{"url": "https://example.com"}`
                                2. Open `GET /{shortCode}` in a browser - it 302-redirects to the original URL
                                3. `GET /api/v1/analytics/{shortCode}` to see the recorded clicks

                                Storage is H2 in-memory; the schema is managed by Flyway migrations.
                                """)
                        .contact(new Contact().name("Agentic SDLC - target system"))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addServersItem(new Server().url("/").description("This host"));
    }
}
