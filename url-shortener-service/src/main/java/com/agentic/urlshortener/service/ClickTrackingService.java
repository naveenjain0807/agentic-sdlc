package com.agentic.urlshortener.service;

import com.agentic.urlshortener.domain.ClickEvent;
import com.agentic.urlshortener.domain.ShortUrl;
import com.agentic.urlshortener.repository.ClickEventRepository;
import com.agentic.urlshortener.repository.ShortUrlRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one {@link ClickEvent} per redirect and keeps the denormalised
 * counter on {@link ShortUrl} in step.
 *
 * <p>Analytics must never break a redirect. Callers are expected to invoke
 * {@link #record} through this bean (not from another {@code @Transactional}
 * method on the same instance, which would bypass the transactional proxy)
 * and catch any exception it throws so a failure here cannot fail the
 * redirect itself.
 */
@Service
public class ClickTrackingService {

    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;

    public ClickTrackingService(ClickEventRepository clickEventRepository, ShortUrlRepository shortUrlRepository) {
        this.clickEventRepository = clickEventRepository;
        this.shortUrlRepository = shortUrlRepository;
    }

    @Transactional
    public void record(ShortUrl shortUrl, HttpServletRequest request) {
        Instant now = Instant.now();
        String userAgent = header(request, "User-Agent");
        ClickEvent event = new ClickEvent(shortUrl.getId(), shortUrl.getShortCode(), now);
        event.setIpAddress(truncate(clientIp(request), 64));
        event.setUserAgent(truncate(userAgent, 512));
        event.setReferer(truncate(header(request, "Referer"), 512));
        event.setDeviceType(DeviceTypes.detect(userAgent));
        clickEventRepository.save(event);
        shortUrlRepository.incrementClickCount(shortUrl.getId(), now);
    }

    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
