package org.jztrmnkl.filterservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jztrmnkl.filterservice.model.Event;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Bot detection — five independent signals; any single positive rejects the event.
 *
 *  1. Null / blank user-agent string.
 *  2. User-agent matches a compiled list of bot / automation UA patterns.
 *  3. Timestamp anomaly: either timestamp field is missing or unparseable.
 *  4. IP event rate exceeds 90 events per 60-second window.
 *  5. Cookie event rate exceeds 20 events per 10-second window.
 */
@Service
public class BotDetectionService {

    // --- thresholds ---------------------------------------------------------
    private static final int MAX_EVENTS_PER_IP_PER_MINUTE  = 90;
    private static final int MAX_EVENTS_PER_COOKIE_PER_10S = 20;

    // --- UA pattern lists ---------------------------------------------------
    private static final List<Pattern> BOT_UA_PATTERNS = List.of(
            // Generic automation keywords (whole-word match to reduce false positives)
            Pattern.compile("(?i)\\b(bot|crawler|spider|scraper|slurp|probe|scan|check|monitor)\\b"),
            // HTTP client libraries (scripts / tools)
            Pattern.compile("(?i)(wget|curl|python-requests|python-urllib|python/\\d|" +
                    "java/\\d|go-http-client|libwww|httpclient|okhttp|axios|node-fetch|" +
                    "got/|undici|httpx|aiohttp|mechanize|scrapy|ruby|perl|" +
                    "guzzle|pycurl|lwp-useragent|restsharp|urllib3)"),
            // Named crawlers and SEO / data bots
            Pattern.compile("(?i)(googlebot|bingbot|yandexbot|baiduspider|duckduckbot|" +
                    "mj12bot|ahrefsbot|semrushbot|dotbot|rogerbot|facebookexternalhit|" +
                    "ia_archiver|petalbot|bytespider|applebot|twitterbot|linkedinbot|" +
                    "slackbot|discordbot|telegrambot|whatsapp|pinterestbot|redditbot)"),
            // Headless and automation frameworks
            Pattern.compile("(?i)(headlesschrome|phantomjs|selenium|webdriver|puppeteer|" +
                    "playwright|chromedp|cypress|nightwatch|testcafe|htmlunit|" +
                    "zombie|casperjs|slimerjs|wkhtmlto)")
    );

    // --- Caffeine caches ----------------------------------------------------
    private final Cache<String, AtomicInteger> ipRateCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    private final Cache<String, AtomicInteger> cookieRateCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build();

    // --- public API ---------------------------------------------------------

    public boolean isBot(Event event) {
        String ua = event.getUserAgent();
        if (isMissingOrBlankUserAgent(ua))            return true;
        if (matchesBotUaPattern(ua))                  return true;
        if (hasAnomalousTimestamp(event))             return true;
        if (isIpRateTooHigh(event.getIp()))           return true;
        if (isCookieRateTooHigh(event.getCookieId())) return true;
        return false;
    }

    // --- signal implementations ---------------------------------------------

    /** Signal 1: missing or blank UA. */
    private boolean isMissingOrBlankUserAgent(String ua) {
        return ua == null || ua.isBlank();
    }

    /** Signal 2: UA matches a known bot / tool pattern. */
    private boolean matchesBotUaPattern(String ua) {
        for (Pattern p : BOT_UA_PATTERNS) {
            if (p.matcher(ua).find()) return true;
        }
        return false;
    }

    /**
     * Signal 3: missing or unparseable timestamps.
     * Both fields must be present and parse as valid ISO-8601 instants.
     */
    private boolean hasAnomalousTimestamp(Event event) {
        String clientTs   = event.getClientTimestamp();
        String receivedAt = event.getReceivedAt();
        if (clientTs == null || clientTs.isBlank() || receivedAt == null || receivedAt.isBlank()) {
            return true;
        }
        try {
            Instant.parse(clientTs);
            Instant.parse(receivedAt);
        } catch (DateTimeParseException e) {
            return true;
        }
        return false;
    }

    /** Signal 4: too many events from one IP within the 60-second window. */
    private boolean isIpRateTooHigh(String ip) {
        if (ip == null || ip.isBlank()) return false;
        AtomicInteger count = ipRateCache.get(ip, k -> new AtomicInteger(0));
        return count.incrementAndGet() > MAX_EVENTS_PER_IP_PER_MINUTE;
    }

    /** Signal 5: too many events from one cookie within the 10-second window. */
    private boolean isCookieRateTooHigh(String cookieId) {
        if (cookieId == null || cookieId.isBlank()) return false;
        AtomicInteger count = cookieRateCache.get(cookieId, k -> new AtomicInteger(0));
        return count.incrementAndGet() > MAX_EVENTS_PER_COOKIE_PER_10S;
    }

}
