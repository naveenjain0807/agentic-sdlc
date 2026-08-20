package com.agentic.urlshortener.service;

import com.agentic.urlshortener.config.AppProperties;
import com.agentic.urlshortener.domain.ClickEvent;
import com.agentic.urlshortener.domain.ShortUrl;
import com.agentic.urlshortener.repository.ClickEventRepository;
import com.agentic.urlshortener.repository.ShortUrlRepository;
import com.agentic.urlshortener.web.ShortUrlMapper;
import com.agentic.urlshortener.web.dto.AnalyticsResponse;
import com.agentic.urlshortener.web.dto.AnalyticsSummaryResponse;
import com.agentic.urlshortener.web.dto.ClickEntry;
import com.agentic.urlshortener.web.dto.CountEntry;
import com.agentic.urlshortener.web.dto.ShortUrlResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side aggregation over the {@code click_event} fact table.
 *
 * <p>Aggregation is done in Java over a bounded window of recent events. That
 * keeps the SQL portable across H2 and Postgres (the production target) at the
 * cost of not scaling past {@link #MAX_EVENTS_SCANNED} events per link - the
 * point at which this would move to a rolled-up daily table anyway.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final int MAX_EVENTS_SCANNED = 5_000;
    private static final String DIRECT = "(direct)";
    private static final int TOP_N = 10;

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final UrlShortenerService urlShortenerService;
    private final ShortUrlMapper mapper;
    private final AppProperties properties;

    public AnalyticsService(ShortUrlRepository shortUrlRepository,
                            ClickEventRepository clickEventRepository,
                            UrlShortenerService urlShortenerService,
                            ShortUrlMapper mapper,
                            AppProperties properties) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.urlShortenerService = urlShortenerService;
        this.mapper = mapper;
        this.properties = properties;
    }

    public AnalyticsResponse forShortCode(String shortCode) {
        ShortUrl shortUrl = urlShortenerService.findByCode(shortCode);
        List<ClickEvent> events = clickEventRepository.findByShortUrlIdOrderByClickedAtDesc(
                shortUrl.getId(), PageRequest.of(0, MAX_EVENTS_SCANNED));

        long uniqueVisitors = events.stream()
                .map(ClickEvent::getIpAddress)
                .filter(ip -> ip != null && !ip.isBlank())
                .distinct()
                .count();

        List<ClickEntry> recent = events.stream()
                .limit(Math.max(1, properties.getRecentClickLimit()))
                .map(e -> new ClickEntry(e.getClickedAt(), e.getIpAddress(), e.getDeviceType(),
                        e.getReferer(), e.getUserAgent()))
                .toList();

        return new AnalyticsResponse(
                shortUrl.getShortCode(),
                urlShortenerService.buildShortUrl(shortUrl.getShortCode()),
                shortUrl.getOriginalUrl(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt(),
                shortUrl.getLastAccessedAt(),
                shortUrl.isActive(),
                shortUrl.isExpired(Instant.now()),
                shortUrl.getTotalClicks(),
                uniqueVisitors,
                clicksByDay(events),
                topBy(events, e -> blankTo(e.getReferer(), DIRECT)),
                topBy(events, e -> blankTo(e.getDeviceType(), DeviceTypes.UNKNOWN)),
                recent);
    }

    public AnalyticsSummaryResponse summary() {
        Instant now = Instant.now();
        long totalUrls = shortUrlRepository.count();
        long activeUrls = shortUrlRepository.countByActiveTrue();
        long expiredUrls = shortUrlRepository.countByActiveTrueAndExpiresAtBefore(now);
        List<ShortUrlResponse> topLinks = shortUrlRepository.findTop10ByActiveTrueOrderByTotalClicksDesc()
                .stream()
                .map(mapper::toResponse)
                .toList();

        return new AnalyticsSummaryResponse(
                now,
                totalUrls,
                activeUrls,
                totalUrls - activeUrls,
                expiredUrls,
                clickEventRepository.count(),
                topLinks);
    }

    // ------------------------------------------------------------------

    private static List<CountEntry> clicksByDay(List<ClickEvent> events) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (ClickEvent event : events) {
            LocalDate day = event.getClickedAt().atZone(ZoneOffset.UTC).toLocalDate();
            byDay.merge(day, 1L, Long::sum);
        }
        Comparator<Map.Entry<LocalDate, Long>> newestFirst =
                (a, b) -> b.getKey().compareTo(a.getKey());
        List<CountEntry> entries = new ArrayList<>(byDay.size());
        byDay.entrySet().stream()
                .sorted(newestFirst)
                .forEach(e -> entries.add(new CountEntry(e.getKey().toString(), e.getValue())));
        return entries;
    }

    private static List<CountEntry> topBy(List<ClickEvent> events, Function<ClickEvent, String> classifier) {
        Map<String, Long> counts = events.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()));
        Comparator<Map.Entry<String, Long>> byCountThenLabel = (a, b) -> {
            int byCount = Long.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        };
        return counts.entrySet().stream()
                .sorted(byCountThenLabel)
                .limit(TOP_N)
                .map(e -> new CountEntry(e.getKey(), e.getValue()))
                .toList();
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
