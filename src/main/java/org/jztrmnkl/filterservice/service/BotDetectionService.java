package org.jztrmnkl.filterservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jztrmnkl.filterservice.model.Event;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Bot detection uses four independent signals; any one is sufficient to flag an event.
 *
 * 1. Null / blank user-agent string.
 * 2. User-agent matches a compiled list of bot / automation UA patterns.
 * 3. IP event rate exceeds 120 events per 60-second window (inhuman throughput).
 * 4. Cookie event rate exceeds 30 events per 10-second window (impossible click speed).
 */
@Service
public class BotDetectionService {

    private static final int MAX_EVENTS_PER_IP_PER_MINUTE   = 120;
    private static final int MAX_EVENTS_PER_COOKIE_PER_10S  = 30;

    private static final List<Pattern> BOT_UA_PATTERNS = List.of(
            // Generic automation signals
            Pattern.compile("(?i)\\b(bot|crawler|spider|scraper|slurp)\\b"),
            Pattern.compile("(?i)(wget|curl|python-requests|python-urllib|java/\\d|go-http-client|" +
                    "libwww|httpclient|okhttp|axios|node-fetch|got/|undici)"),
            // Named crawlers and SEO bots
            Pattern.compile("(?i)(googlebot|bingbot|yandexbot|baiduspider|duckduckbot|" +
                    "mj12bot|ahrefsbot|semrushbot|dotbot|rogerbot|facebookexternalhit|" +
                    "ia_archiver|petalbot|bytespider)"),
            // Headless / automation frameworks
            Pattern.compile("(?i)(headlesschrome|phantomjs|selenium|webdriver|puppeteer|" +
                    "playwright|chromedp|cypress|nightwatch|testcafe)")
    );

    // Counts per IP, reset every 60 seconds from first event
    private final Cache<String, AtomicInteger> ipRateCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    // Counts per cookie, reset every 10 seconds from first event
    private final Cache<String, AtomicInteger> cookieRateCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build();

    public boolean isBot(Event event) {
        if (isSuspiciousUserAgent(event.getUserAgent())) return true;
        if (isIpRateTooHigh(event.getIp()))              return true;
        if (isCookieRateTooHigh(event.getCookieId()))    return true;
        return false;
    }

    private boolean isSuspiciousUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return true;
        for (Pattern p : BOT_UA_PATTERNS) {
            if (p.matcher(ua).find()) return true;
        }
        return false;
    }

    private boolean isIpRateTooHigh(String ip) {
        if (ip == null || ip.isBlank()) return false;
        AtomicInteger count = ipRateCache.get(ip, k -> new AtomicInteger(0));
        return count.incrementAndGet() > MAX_EVENTS_PER_IP_PER_MINUTE;
    }

    private boolean isCookieRateTooHigh(String cookieId) {
        if (cookieId == null || cookieId.isBlank()) return false;
        AtomicInteger count = cookieRateCache.get(cookieId, k -> new AtomicInteger(0));
        return count.incrementAndGet() > MAX_EVENTS_PER_COOKIE_PER_10S;
    }
}
