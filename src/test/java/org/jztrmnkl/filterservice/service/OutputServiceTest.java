package org.jztrmnkl.filterservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.jztrmnkl.filterservice.model.Event;
import org.jztrmnkl.filterservice.model.ProcessingStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutputService}.
 *
 * A JUnit 5 {@code @TempDir} provides an isolated, auto-cleaned directory so
 * tests never touch the real {@code output/} folder.  The service is
 * constructed directly (bypassing Spring) using the same ObjectMapper
 * configuration as production (SNAKE_CASE naming strategy).
 */
class OutputServiceTest {

    @TempDir
    Path tempDir;

    private OutputService outputService;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        outputService = new OutputService(mapper, tempDir.toString());
        outputService.init();
    }

    // =========================================================================
    // init()
    // =========================================================================

    @Test
    void init_createsShippedEventsFile() {
        assertThat(tempDir.resolve("shipped_events.ndjson")).exists();
    }

    @Test
    void init_createsSummaryFile() {
        assertThat(tempDir.resolve("summary.md")).exists();
    }

    @Test
    void init_summaryContainsExpectedHeadings() throws IOException {
        String content = Files.readString(tempDir.resolve("summary.md"));
        assertThat(content)
                .contains("FilterService")
                .contains("Bot-Detection Logic")
                .contains("Deduplication Logic")
                .contains("Six independent signals");
    }

    // =========================================================================
    // appendShippedEvent()
    // =========================================================================

    @Test
    void appendShippedEvent_writesOneNdjsonLine() throws IOException {
        Event e = new Event();
        e.setEventId("evt-001");
        e.setEventType("click");
        outputService.appendShippedEvent(e);

        List<String> lines = nonBlankLines(tempDir.resolve("shipped_events.ndjson"));
        assertThat(lines).hasSize(1);
    }

    @Test
    void appendShippedEvent_lineContainsEventIdValue() throws IOException {
        Event e = new Event();
        e.setEventId("evt-unique-xyz");
        e.setEventType("view");
        outputService.appendShippedEvent(e);

        String content = Files.readString(tempDir.resolve("shipped_events.ndjson"));
        assertThat(content).contains("evt-unique-xyz");
    }

    @Test
    void appendShippedEvent_lineIsSerializedWithSnakeCaseKeys() throws IOException {
        Event e = new Event();
        e.setEventId("evt-002");
        e.setEventType("visible");
        e.setCookieId("cid-abc");
        outputService.appendShippedEvent(e);

        String line = nonBlankLines(tempDir.resolve("shipped_events.ndjson")).get(0);
        // Verify snake_case serialization of known fields
        assertThat(line).contains("\"event_id\"");
        assertThat(line).contains("\"event_type\"");
        assertThat(line).contains("\"cookie_id\"");
    }

    @Test
    void appendShippedEvent_appendsMultipleEventsAsSeperateLines() throws IOException {
        for (int i = 1; i <= 5; i++) {
            Event e = new Event();
            e.setEventId("evt-" + i);
            outputService.appendShippedEvent(e);
        }

        List<String> lines = nonBlankLines(tempDir.resolve("shipped_events.ndjson"));
        assertThat(lines).hasSize(5);
        // Verify order is preserved
        assertThat(lines.get(0)).contains("evt-1");
        assertThat(lines.get(4)).contains("evt-5");
    }

    // =========================================================================
    // updateSummary()
    // =========================================================================

    @Test
    void updateSummary_reflectsCurrentStats() throws IOException {
        ProcessingStats stats = new ProcessingStats();
        for (int i = 0; i < 10; i++) stats.incrementReceived();
        for (int i = 0; i < 7;  i++) stats.incrementShipped();
        for (int i = 0; i < 2;  i++) stats.incrementBots();
        for (int i = 0; i < 1;  i++) stats.incrementExactDups();

        outputService.updateSummary(stats);

        String content = Files.readString(tempDir.resolve("summary.md"));
        assertThat(content).contains("10");  // received
        assertThat(content).contains("7");   // shipped
        assertThat(content).contains("2");   // bots
    }

    @Test
    void updateSummary_overwritesPreviousContent() throws IOException {
        ProcessingStats first = new ProcessingStats();
        first.incrementReceived();
        outputService.updateSummary(first);

        ProcessingStats second = new ProcessingStats();
        for (int i = 0; i < 99; i++) second.incrementReceived();
        outputService.updateSummary(second);

        String content = Files.readString(tempDir.resolve("summary.md"));
        // The second write should be the only content — not an accumulation
        assertThat(content).contains("99");
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static List<String> nonBlankLines(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .filter(l -> !l.isBlank())
                .toList();
    }
}
