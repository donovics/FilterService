package org.jztrmnkl.filterservice.service;

import org.jztrmnkl.filterservice.model.Event;
import org.jztrmnkl.filterservice.model.FilterReason;
import org.jztrmnkl.filterservice.model.ProcessingStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pipeline: bot check → dedup check → accept.
 *
 * Bot check comes first so that bot event_ids never enter the dedup caches;
 * this prevents a bot from poisoning legitimate-user dedup slots.
 */
@Service
public class EventProcessingService {

    private static final Logger log = LoggerFactory.getLogger(EventProcessingService.class);

    private final BotDetectionService   botService;
    private final DeduplicationService  dedupService;
    private final OutputService         outputService;

    private final ProcessingStats stats          = new ProcessingStats();
    private final List<Event>     acceptedEvents = new CopyOnWriteArrayList<>();

    public EventProcessingService(BotDetectionService botService,
                                  DeduplicationService dedupService,
                                  OutputService outputService) {
        this.botService    = botService;
        this.dedupService  = dedupService;
        this.outputService = outputService;
    }

    public FilterReason process(Event event) {
        stats.incrementReceived();

        // 1 — bot detection (caches not touched for bots)
        if (botService.isBot(event)) {
            stats.incrementBots();
            log.debug("BOT      event_id={} ip={} ua={}",
                    event.getEventId(), event.getIp(), event.getUserAgent());
            outputService.updateSummary(stats);
            return FilterReason.BOT_TRAFFIC;
        }

        // 2 — deduplication
        FilterReason classification = dedupService.classify(event);

        if (classification == FilterReason.EXACT_DUPLICATE) {
            stats.incrementExactDups();
            log.debug("EXACT_DUP event_id={}", event.getEventId());
            outputService.updateSummary(stats);
            return FilterReason.EXACT_DUPLICATE;
        }

        if (classification == FilterReason.NEAR_DUPLICATE) {
            stats.incrementNearDups();
            log.debug("NEAR_DUP  event_id={} cookie={} type={} ts={}",
                    event.getEventId(), event.getCookieId(),
                    event.getEventType(), event.getClientTimestamp());
            outputService.updateSummary(stats);
            return FilterReason.NEAR_DUPLICATE;
        }

        // 3 — accept: record in caches and forward
        dedupService.record(event);
        acceptedEvents.add(event);
        stats.incrementShipped();
        log.debug("SHIPPED   event_id={}", event.getEventId());

        outputService.appendShippedEvent(event);
        outputService.updateSummary(stats);

        return FilterReason.ACCEPTED;
    }

    public List<Event> getAcceptedEvents() {
        return Collections.unmodifiableList(acceptedEvents);
    }

    public ProcessingStats getStats() {
        return stats;
    }
}
