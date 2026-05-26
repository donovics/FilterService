package org.jztrmnkl.filterservice.service;

import org.jztrmnkl.filterservice.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BotDetectionService}.
 *
 * A fresh service instance is created before each test so that the
 * stateful Caffeine rate-limit caches always start empty.
 */
class BotDetectionServiceTest {

    private BotDetectionService service;

    @BeforeEach
    void setUp() {
        service = new BotDetectionService();
    }

    // -------------------------------------------------------------------------
    // Helper: returns a fully valid event that passes every signal on its own
    // -------------------------------------------------------------------------

    private static Event cleanEvent() {
        Event e = new Event();
        e.setEventId("evt-test-001");
        e.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        e.setClientTimestamp("2024-01-15T10:00:00Z");
        e.setReceivedAt("2024-01-15T10:00:00.123Z");
        e.setIp("203.0.113.42");
        e.setCookieId("cid-test-abc");
        e.setEventType("click");
        return e;
    }

    // =========================================================================
    // Signal 1 — Missing / blank user-agent
    // =========================================================================

    @Test
    void nullUserAgent_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setUserAgent(null);
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void blankUserAgent_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setUserAgent("");
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void whitespaceOnlyUserAgent_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setUserAgent("   ");
        assertThat(service.isBot(e)).isTrue();
    }

    // =========================================================================
    // Signal 2 — UA keyword patterns
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            // Named crawlers
            "Googlebot/2.1 (+http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)",
            "Mozilla/5.0 (compatible; SemrushBot/7~bl; +http://www.semrush.com/bot.html)",
            // Generic keywords (word-boundary match)
            "web-crawler/2.0",
            "my-spider/1.0",
            // HTTP libraries
            "python-requests/2.28.0",
            "curl/7.88.1",
            "okhttp/4.10.0",
            // Headless / automation frameworks
            "HeadlessChrome/113.0.5672.126",
            "Mozilla/5.0 (X11; Linux x86_64) Selenium/4.0 WebDriver",
            "Playwright/1.40 node/20"
    })
    void botUserAgents_areDetectedAsBot(String ua) {
        Event e = cleanEvent();
        e.setUserAgent(ua);
        assertThat(service.isBot(e)).as("Expected UA to be flagged as bot: %s", ua).isTrue();
    }

    @Test
    void legitimateBrowserUserAgent_isNotFlaggedByUaPatterns() {
        // cleanEvent() already uses a realistic Chrome UA — verify it is clean
        assertThat(service.isBot(cleanEvent())).isFalse();
    }

    @Test
    void firefoxUserAgent_isNotFlaggedByUaPatterns() {
        Event e = cleanEvent();
        e.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0");
        assertThat(service.isBot(e)).isFalse();
    }

    // =========================================================================
    // Signal 3 — Timestamp presence and parseability
    // =========================================================================

    @Test
    void nullClientTimestamp_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setClientTimestamp(null);
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void blankClientTimestamp_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setClientTimestamp("   ");
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void unparsableClientTimestamp_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setClientTimestamp("not-a-real-date");
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void nullReceivedAt_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setReceivedAt(null);
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void blankReceivedAt_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setReceivedAt("");
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void unparsableReceivedAt_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setReceivedAt("2024/01/15 10:00:00"); // not ISO-8601 Instant
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void validIso8601Timestamps_areNotFlagged() {
        // cleanEvent() already has valid timestamps — just confirm it passes
        assertThat(service.isBot(cleanEvent())).isFalse();
    }

    // =========================================================================
    // Signal 4 — IP event-rate limit (90 / 60 s)
    // =========================================================================

    @Test
    void ipAtRateLimit_isNotFlagged() {
        Event e = cleanEvent();
        e.setIp("10.0.0.1");
        // Null cookie so the cookie-rate signal (20/10s) cannot interfere
        // while we drive 90 events through the IP-rate check.
        e.setCookieId(null);

        // First 90 events should all pass
        for (int i = 0; i < 90; i++) {
            assertThat(service.isBot(e))
                    .as("Event %d should not be flagged", i + 1)
                    .isFalse();
        }
    }

    @Test
    void ipExceedingRateLimit_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setIp("10.0.0.2");
        e.setCookieId(null); // isolate the IP-rate signal
        for (int i = 0; i < 90; i++) {
            service.isBot(e);
        }
        // 91st event from the same IP must be flagged
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void nullIp_isNotFlaggedByIpRateLimit() {
        Event e = cleanEvent();
        e.setIp(null);
        assertThat(service.isBot(e)).isFalse();
    }

    @Test
    void blankIp_isNotFlaggedByIpRateLimit() {
        Event e = cleanEvent();
        e.setIp("");
        assertThat(service.isBot(e)).isFalse();
    }

    // =========================================================================
    // Signal 5 — Cookie event-rate limit (20 / 10 s)
    // =========================================================================

    @Test
    void cookieAtRateLimit_isNotFlagged() {
        Event e = cleanEvent();
        e.setCookieId("cid-rate-ok");

        for (int i = 0; i < 20; i++) {
            assertThat(service.isBot(e))
                    .as("Event %d should not be flagged", i + 1)
                    .isFalse();
        }
    }

    @Test
    void cookieExceedingRateLimit_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setCookieId("cid-rate-bad");

        for (int i = 0; i < 20; i++) {
            service.isBot(e);
        }
        // 21st event from the same cookie must be flagged
        assertThat(service.isBot(e)).isTrue();
    }

    @Test
    void nullCookieId_isNotFlaggedByCookieRateLimit() {
        Event e = cleanEvent();
        e.setCookieId(null);
        assertThat(service.isBot(e)).isFalse();
    }

    @Test
    void blankCookieId_isNotFlaggedByCookieRateLimit() {
        Event e = cleanEvent();
        e.setCookieId("  ");
        assertThat(service.isBot(e)).isFalse();
    }

    // =========================================================================
    // Signal 6 — Invalid event type
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {"view", "visible", "click"})
    void allowedEventTypes_areNotFlagged(String type) {
        Event e = cleanEvent();
        e.setEventType(type);
        assertThat(service.isBot(e)).isFalse();
    }

    @Test
    void nullEventType_isDetectedAsBot() {
        Event e = cleanEvent();
        e.setEventType(null);
        assertThat(service.isBot(e)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pageview", "impression", "hover", "", "CLICK"})
    void invalidEventTypes_areDetectedAsBot(String type) {
        Event e = cleanEvent();
        e.setEventType(type);
        assertThat(service.isBot(e))
                .as("Expected event_type '%s' to be flagged as bot", type)
                .isTrue();
    }
}
