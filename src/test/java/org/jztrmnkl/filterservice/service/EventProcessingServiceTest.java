package org.jztrmnkl.filterservice.service;

import org.jztrmnkl.filterservice.model.Event;
import org.jztrmnkl.filterservice.model.FilterReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventProcessingService}.
 *
 * All three collaborators are mocked so that each test exercises only the
 * pipeline orchestration logic (routing, counter increments, accepted-event
 * list management) without touching real caches or the filesystem.
 */
@ExtendWith(MockitoExtension.class)
class EventProcessingServiceTest {

    @Mock BotDetectionService   botService;
    @Mock DeduplicationService  dedupService;
    @Mock OutputService         outputService;

    private EventProcessingService processingService;
    private Event                  event;

    @BeforeEach
    void setUp() {
        processingService = new EventProcessingService(botService, dedupService, outputService);

        event = new Event();
        event.setEventId("evt-001");
        event.setEventType("click");
        event.setCookieId("cid-abc");
    }

    // =========================================================================
    // Bot path
    // =========================================================================

    @Test
    void botEvent_returnsBotTraffic() {
        when(botService.isBot(event)).thenReturn(true);

        assertThat(processingService.process(event)).isEqualTo(FilterReason.BOT_TRAFFIC);
    }

    @Test
    void botEvent_incrementsBotsCounter_notShipped() {
        when(botService.isBot(event)).thenReturn(true);
        processingService.process(event);

        assertThat(processingService.getStats().getBots()).isEqualTo(1);
        assertThat(processingService.getStats().getShipped()).isEqualTo(0);
    }

    @Test
    void botEvent_neverTouchesDedupCachesOrShipOutput() {
        when(botService.isBot(event)).thenReturn(true);
        processingService.process(event);

        verify(dedupService, never()).classify(any());
        verify(dedupService, never()).record(any());
        verify(outputService, never()).appendShippedEvent(any());
    }

    @Test
    void botEvent_neverAppearsInAcceptedEventsList() {
        when(botService.isBot(event)).thenReturn(true);
        processingService.process(event);

        assertThat(processingService.getAcceptedEvents()).isEmpty();
    }

    // =========================================================================
    // Exact-duplicate path
    // =========================================================================

    @Test
    void exactDuplicate_returnsExactDuplicate_andIncrementsCounter() {
        when(botService.isBot(event)).thenReturn(false);
        when(dedupService.classify(event)).thenReturn(FilterReason.EXACT_DUPLICATE);

        assertThat(processingService.process(event)).isEqualTo(FilterReason.EXACT_DUPLICATE);
        assertThat(processingService.getStats().getExactDups()).isEqualTo(1);
        assertThat(processingService.getStats().getShipped()).isEqualTo(0);
    }

    @Test
    void exactDuplicate_neverRecordedOrShipped() {
        when(botService.isBot(event)).thenReturn(false);
        when(dedupService.classify(event)).thenReturn(FilterReason.EXACT_DUPLICATE);
        processingService.process(event);

        verify(dedupService, never()).record(any());
        verify(outputService, never()).appendShippedEvent(any());
    }

    // =========================================================================
    // Near-duplicate path
    // =========================================================================

    @Test
    void nearDuplicate_returnsNearDuplicate_andIncrementsCounter() {
        when(botService.isBot(event)).thenReturn(false);
        when(dedupService.classify(event)).thenReturn(FilterReason.NEAR_DUPLICATE);

        assertThat(processingService.process(event)).isEqualTo(FilterReason.NEAR_DUPLICATE);
        assertThat(processingService.getStats().getNearDups()).isEqualTo(1);
        assertThat(processingService.getStats().getShipped()).isEqualTo(0);
    }

    @Test
    void nearDuplicate_neverRecordedOrShipped() {
        when(botService.isBot(event)).thenReturn(false);
        when(dedupService.classify(event)).thenReturn(FilterReason.NEAR_DUPLICATE);
        processingService.process(event);

        verify(dedupService, never()).record(any());
        verify(outputService, never()).appendShippedEvent(any());
    }

    // =========================================================================
    // Accepted path
    // =========================================================================

    @Test
    void acceptedEvent_returnsAccepted_andIncrementsShippedCounter() {
        when(botService.isBot(event)).thenReturn(false);
        when(dedupService.classify(event)).thenReturn(FilterReason.ACCEPTED);

        assertThat(processingService.process(event)).isEqualTo(FilterReason.ACCEPTED);
        assertThat(processingService.getStats().getShipped()).isEqualTo(1);
    }

    @Test
    void acceptedEvent_isRecordedInDedupCacheAndForwardedToOutput() {
        when(botService.isBot(event)).thenReturn(false);
        when(dedupService.classify(event)).thenReturn(FilterReason.ACCEPTED);

        processingService.process(event);

        verify(dedupService).record(event);
        verify(outputService).appendShippedEvent(event);
    }

    @Test
    void acceptedEvent_appearsInAcceptedEventsList() {
        when(botService.isBot(event)).thenReturn(false);
        when(dedupService.classify(event)).thenReturn(FilterReason.ACCEPTED);

        processingService.process(event);

        assertThat(processingService.getAcceptedEvents()).containsExactly(event);
    }

    // =========================================================================
    // Stats accumulation across multiple events
    // =========================================================================

    @Test
    void receivedCounter_incrementsForEveryEvent_regardlessOfOutcome() {
        when(botService.isBot(any())).thenReturn(true);

        processingService.process(event);
        processingService.process(event);
        processingService.process(event);

        assertThat(processingService.getStats().getReceived()).isEqualTo(3);
    }

    @Test
    void mixedEvents_statsAccumulateCorrectly() {
        Event bot  = new Event(); bot.setEventId("bot-1");
        Event dup  = new Event(); dup.setEventId("dup-1");
        Event good = new Event(); good.setEventId("good-1");

        when(botService.isBot(bot)).thenReturn(true);
        when(botService.isBot(dup)).thenReturn(false);
        when(botService.isBot(good)).thenReturn(false);
        when(dedupService.classify(dup)).thenReturn(FilterReason.EXACT_DUPLICATE);
        when(dedupService.classify(good)).thenReturn(FilterReason.ACCEPTED);

        processingService.process(bot);
        processingService.process(dup);
        processingService.process(good);

        assertThat(processingService.getStats().getReceived()).isEqualTo(3);
        assertThat(processingService.getStats().getBots()).isEqualTo(1);
        assertThat(processingService.getStats().getExactDups()).isEqualTo(1);
        assertThat(processingService.getStats().getShipped()).isEqualTo(1);
    }
}
