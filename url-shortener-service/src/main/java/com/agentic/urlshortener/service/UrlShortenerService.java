package com.agentic.urlshortener.service;

import com.agentic.urlshortener.config.AppProperties;
import com.agentic.urlshortener.domain.ShortUrl;
import com.agentic.urlshortener.exception.AliasUnavailableException;
import com.agentic.urlshortener.exception.InvalidUrlException;
import com.agentic.urlshortener.exception.ShortUrlExpiredException;
import com.agentic.urlshortener.exception.ShortUrlNotFoundException;
import com.agentic.urlshortener.repository.ShortUrlRepository;
import com.agentic.urlshortener.web.dto.CreateShortUrlRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core use cases: create, resolve, inspect and retire short links.
 */
@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final AppProperties properties;

    public UrlShortenerService(ShortUrlRepository shortUrlRepository,
                               ShortCodeGenerator shortCodeGenerator,
                               AppProperties properties) {
        this.shortUrlRepository = shortUrlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.properties = properties;
    }

    /** Result of a create call: {@code created} is false when an existing link was re-used. */
    public record CreateResult(ShortUrl shortUrl, boolean created) {
    }

    @Transactional
    public CreateResult create(CreateShortUrlRequest request, String idempotencyKey) {
        Instant now = Instant.now();
        String normalisedUrl = validateAndNormalise(request.url());
        String urlHash = sha256Hex(normalisedUrl);
        Instant expiresAt = resolveExpiry(request, now);

        // 1. Explicit idempotency key wins: same key always yields the same link.
        if (hasText(idempotencyKey)) {
            Optional<ShortUrl> existing =
                    shortUrlRepository.findFirstByIdempotencyKeyOrderByIdAsc(idempotencyKey.trim());
            if (existing.isPresent()) {
                log.debug("Idempotency key {} replayed, returning code {}", idempotencyKey, existing.get().getShortCode());
                return new CreateResult(existing.get(), false);
            }
        }

        // 2. Plain "shorten this URL" calls are de-duplicated so the same input
        //    does not accumulate a new row on every retry.
        boolean deduplicable = !hasText(request.customAlias()) && expiresAt == null && !hasText(idempotencyKey);
        if (deduplicable) {
            Optional<ShortUrl> existing =
                    shortUrlRepository.findFirstByUrlHashAndActiveTrueAndExpiresAtIsNullOrderByIdAsc(urlHash);
            if (existing.isPresent() && existing.get().getOriginalUrl().equals(normalisedUrl)) {
                return new CreateResult(existing.get(), false);
            }
        }

        String code = resolveShortCode(request.customAlias());

        ShortUrl shortUrl = new ShortUrl(code, normalisedUrl, urlHash, now);
        shortUrl.setExpiresAt(expiresAt);
        shortUrl.setCreatedBy(hasText(request.createdBy()) ? request.createdBy().trim() : "api");
        shortUrl.setIdempotencyKey(hasText(idempotencyKey) ? idempotencyKey.trim() : null);

        ShortUrl saved = shortUrlRepository.saveAndFlush(shortUrl);
        log.info("Created short link {} -> {}", saved.getShortCode(), saved.getOriginalUrl());
        return new CreateResult(saved, true);
    }

    /** Looks the code up without applying activity/expiry rules. */
    @Transactional(readOnly = true)
    public ShortUrl findByCode(String shortCode) {
        return shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
    }

    /**
     * Resolves a code for redirection.
     *
     * @throws ShortUrlNotFoundException when unknown or soft-deleted
     * @throws ShortUrlExpiredException  when past its expiry instant
     */
    @Transactional(readOnly = true)
    public ShortUrl resolveForRedirect(String shortCode) {
        ShortUrl shortUrl = findByCode(shortCode);
        if (!shortUrl.isActive()) {
            throw new ShortUrlNotFoundException(shortCode);
        }
        if (shortUrl.isExpired(Instant.now())) {
            throw new ShortUrlExpiredException(shortCode, shortUrl.getExpiresAt());
        }
        return shortUrl;
    }

    @Transactional(readOnly = true)
    public Page<ShortUrl> list(Pageable pageable, boolean activeOnly) {
        return activeOnly ? shortUrlRepository.findAllByActiveTrue(pageable) : shortUrlRepository.findAll(pageable);
    }

    /** Soft delete: the code is retired but its click history is preserved. */
    @Transactional
    public void deactivate(String shortCode) {
        ShortUrl shortUrl = findByCode(shortCode);
        shortUrl.setActive(false);
        shortUrl.setUpdatedAt(Instant.now());
        shortUrlRepository.save(shortUrl);
        log.info("Deactivated short link {}", shortCode);
    }

    public String buildShortUrl(String shortCode) {
        return properties.normalisedBaseUrl() + "/" + shortCode;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private String resolveShortCode(String customAlias) {
        if (!hasText(customAlias)) {
            return shortCodeGenerator.nextCode();
        }
        String alias = customAlias.trim();
        if (ShortCodeGenerator.RESERVED_CODES.contains(alias.toLowerCase(Locale.ROOT))
                || shortUrlRepository.existsByShortCode(alias)) {
            throw new AliasUnavailableException(alias);
        }
        return alias;
    }

    private Instant resolveExpiry(CreateShortUrlRequest request, Instant now) {
        if (request.expiresAt() != null && request.ttlSeconds() != null) {
            throw new InvalidUrlException("Provide either ttlSeconds or expiresAt, not both");
        }
        if (request.expiresAt() != null) {
            if (!request.expiresAt().isAfter(now)) {
                throw new InvalidUrlException("expiresAt must be in the future");
            }
            return request.expiresAt();
        }
        if (request.ttlSeconds() != null) {
            return now.plusSeconds(request.ttlSeconds());
        }
        long defaultTtl = properties.getDefaultTtlSeconds();
        return defaultTtl > 0 ? now.plusSeconds(defaultTtl) : null;
    }

    private String validateAndNormalise(String rawUrl) {
        if (!hasText(rawUrl)) {
            throw new InvalidUrlException("url must not be blank");
        }
        String candidate = rawUrl.trim();
        if (candidate.length() > properties.getMaxUrlLength()) {
            throw new InvalidUrlException("url exceeds the maximum length of " + properties.getMaxUrlLength());
        }
        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("url is not a valid URI: " + ex.getReason());
        }
        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw new InvalidUrlException("url must be absolute and start with http:// or https://");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new InvalidUrlException("Only http and https URLs can be shortened, got '" + scheme + "'");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must contain a host");
        }
        return candidate;
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
