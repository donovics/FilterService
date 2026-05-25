package org.jztrmnkl.filterservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jztrmnkl.filterservice.model.Event;
import org.jztrmnkl.filterservice.model.ProcessingStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Service
public class OutputService {

    private static final Logger log = LoggerFactory.getLogger(OutputService.class);

    private static final Path OUTPUT_DIR          = Path.of("output");
    private static final Path SHIPPED_EVENTS_FILE = OUTPUT_DIR.resolve("shipped_events.ndjson");
    private static final Path SUMMARY_FILE        = OUTPUT_DIR.resolve("summary.md");

    private final ObjectMapper objectMapper;

    public OutputService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        if (!Files.exists(SHIPPED_EVENTS_FILE)) {
            Files.createFile(SHIPPED_EVENTS_FILE);
        }
        updateSummary(new ProcessingStats());
        log.info("Output directory ready at {}", OUTPUT_DIR.toAbsolutePath());
    }

    public synchronized void appendShippedEvent(Event event) {
        try {
            String line = objectMapper.writeValueAsString(event) + "\n";
            Files.writeString(SHIPPED_EVENTS_FILE, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write shipped event", e);
        }
    }

    public synchronized void updateSummary(ProcessingStats stats) {
        try {
            Files.writeString(SUMMARY_FILE, buildSummary(stats));
        } catch (IOException e) {
            log.error("Failed to update summary", e);
        }
    }

    private String buildSummary(ProcessingStats s) {
        long totalFiltered = s.getExactDups() + s.getNearDups() + s.getBots();
        double shipRate = s.getReceived() == 0 ? 0.0
                : 100.0 * s.getShipped() / s.getReceived();

        return String.format("""
                # FilterService — Processing Summary

                _Last updated: %s_

                ## Event Counts

                | Metric              | Count  |
                |---------------------|--------|
                | Received            | %6d |
                | Exact duplicates    | %6d |
                | Near-duplicates     | %6d |
                | Bot traffic         | %6d |
                | **Shipped**         | **%d** |

                Ship rate: %.1f%% of received events forwarded to the downstream queue.

                ---

                ## Deduplication Logic

                Events are filtered in two passes, applied in order before bot detection so that
                the deduplication caches only ever contain records of accepted, clean events.

                **Exact duplicates** — The `event_id` of every accepted event is stored in a
                Caffeine cache with a **10-minute TTL**. If the same `event_id` arrives again
                within that window it is discarded as a retry duplicate. After 10 minutes the
                key expires, so a second legitimate event that happens to share an `event_id`
                with an old one is allowed through.

                **Near-duplicates** — Even when a client generates a fresh `event_id` on each
                retry, the payload is still recognisably the same event. A composite key of
                `(cookie_id + event_type + client_timestamp)` is stored in a second cache with
                a **60-second TTL**. An event whose composite key is already in the cache is
                discarded. Because `client_timestamp` is the browser-side time of the action,
                two truly separate actions of the same type by the same user will have
                different timestamps and will both be accepted.

                ---

                ## Bot-Detection Logic

                Four independent signals are evaluated; a single positive is enough to reject
                an event. Bot events are counted but never forwarded or added to the
                deduplication caches.

                1. **Missing / blank user-agent** — a browser always sends a UA string.
                   Events without one are not produced by real browsers.

                2. **User-agent pattern matching** — the UA string is tested against compiled
                   regex patterns covering generic automation keywords (`bot`, `crawler`,
                   `spider`, `scraper`, `slurp`), common HTTP libraries (`curl`, `wget`,
                   `python-requests`, `okhttp`, `go-http-client`, …), named crawlers
                   (Googlebot, Bingbot, AhrefsBot, SemrushBot, …), and headless / automation
                   frameworks (HeadlessChrome, PhantomJS, Selenium, Puppeteer, Playwright, …).

                3. **IP event-rate limit** — a per-IP counter resets every 60 seconds via a
                   Caffeine expiry. Any IP that exceeds **%d events per minute** is flagged.
                   Real users do not generate that volume from a single address.

                4. **Cookie event-rate limit** — a per-cookie counter resets every 10 seconds.
                   Any cookie that exceeds **%d events per 10 seconds** is flagged.
                   That rate is faster than any human interaction pattern.

                ---

                _Total filtered (dups + bots): %d / %d received_
                """,
                Instant.now(),
                s.getReceived(),
                s.getExactDups(),
                s.getNearDups(),
                s.getBots(),
                s.getShipped(),
                shipRate,
                120,   // MAX_EVENTS_PER_IP_PER_MINUTE
                30,    // MAX_EVENTS_PER_COOKIE_PER_10S
                totalFiltered,
                s.getReceived()
        );
    }
}
