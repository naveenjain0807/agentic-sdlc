package com.agentic.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("shorten -> redirect -> analytics records the click")
    void fullHappyPath() throws Exception {
        String target = "https://example.com/full-happy-path";
        String code = createShortLink("{\"url\":\"" + target + "\"}");

        mockMvc.perform(get("/" + code).header("User-Agent", "Mozilla/5.0 (Macintosh) Chrome/120"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", target));

        mockMvc.perform(get("/api/v1/analytics/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(code))
                .andExpect(jsonPath("$.originalUrl").value(target))
                .andExpect(jsonPath("$.totalClicks").value(1))
                .andExpect(jsonPath("$.uniqueVisitors").value(1))
                .andExpect(jsonPath("$.clicksByDay[0].count").value(1))
                .andExpect(jsonPath("$.clicksByDeviceType[0].label").value("DESKTOP"))
                .andExpect(jsonPath("$.recentClicks.length()").value(1));
    }

    @Test
    @DisplayName("the same URL is de-duplicated instead of creating a second code")
    void duplicateUrlsAreDeduplicated() throws Exception {
        String body = "{\"url\":\"https://example.com/dedupe-me\"}";
        String first = createShortLink(body);

        MvcResult second = mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(read(second).path("shortCode").asText()).isEqualTo(first);
    }

    @Test
    @DisplayName("Idempotency-Key makes repeated creates return the same link")
    void idempotencyKeyIsHonoured() throws Exception {
        String key = "order-4711";
        MvcResult first = mockMvc.perform(post("/api/v1/shorten")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/idem-a\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult replay = mockMvc.perform(post("/api/v1/shorten")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/idem-b\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(read(replay).path("shortCode").asText())
                .isEqualTo(read(first).path("shortCode").asText());
        assertThat(read(replay).path("originalUrl").asText()).isEqualTo("https://example.com/idem-a");
    }

    @Test
    @DisplayName("custom aliases work once and then conflict")
    void customAliasIsUniqueAndUsable() throws Exception {
        String body = "{\"url\":\"https://example.com/vanity\",\"customAlias\":\"my-vanity\"}";

        mockMvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("my-vanity"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/my-vanity"));

        mockMvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/my-vanity"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/vanity"));
    }

    @Test
    @DisplayName("delete retires the code but keeps its history")
    void deleteIsSoftAndStopsRedirects() throws Exception {
        String code = createShortLink("{\"url\":\"https://example.com/to-be-deleted\"}");

        mockMvc.perform(get("/" + code)).andExpect(status().isFound());
        mockMvc.perform(delete("/api/v1/urls/" + code)).andExpect(status().isNoContent());
        mockMvc.perform(get("/" + code)).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/urls/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.totalClicks").value(1));
    }

    @Test
    @DisplayName("bad input is rejected with a structured error")
    void validationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.url").isNotEmpty());

        mockMvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://files.example.com/report.pdf\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/past\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/both\",\"ttlSeconds\":60,"
                                + "\"expiresAt\":\"2999-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unknown codes 404 rather than redirecting")
    void unknownCode() throws Exception {
        mockMvc.perform(get("/nope404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Flyway seed data is present and redirects")
    void seededLinksAreAvailable() throws Exception {
        mockMvc.perform(get("/welcome"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://spring.io/projects/spring-boot"));
    }

    @Test
    @DisplayName("listing and the global summary respond")
    void listingAndSummary() throws Exception {
        createShortLink("{\"url\":\"https://example.com/listed\"}");

        mockMvc.perform(get("/api/v1/urls?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(5));

        mockMvc.perform(get("/api/v1/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUrls").isNumber())
                .andExpect(jsonPath("$.topLinks").isArray());
    }

    @Test
    @DisplayName("OpenAPI document and Swagger UI are served")
    void openApiIsExposed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/shorten']").exists())
                .andExpect(jsonPath("$.info.title").value("URL Shortener API"));
    }

    @Test
    @DisplayName("actuator health is up")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ------------------------------------------------------------------

    private String createShortLink(String jsonBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).path("shortCode").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
