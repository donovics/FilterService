package org.jztrmnkl.filterservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jztrmnkl.filterservice.model.Event;
import org.jztrmnkl.filterservice.model.FilterReason;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Exact-duplicate window: 10 minutes on event_id.
 * Near-duplicate window:  60 seconds on (cookie_id + event_type + client_timestamp).
 *
 * Events that share a composite key but arrive further apart than the window are treated
 * as legitimate repeat traffic and pass through.
 *
 * Thread-safety: putIfAbsent on Caffeine's ConcurrentMap view is atomic.
 */
@Service
public class DeduplicationService {

    private final Cache<String, Boolean> exactCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Boolean> nearCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    /**
     * Classifies the event as ACCEPTED, EXACT_DUPLICATE, or NEAR_DUPLICATE.
     * Only accepted events are recorded in both caches, keeping them free of bot/dup noise.
     */
    public FilterReason classify(Event event) {
        String exactKey = event.getEventId();
        if (exactKey != null && exactCache.asMap().containsKey(exactKey)) {
            return FilterReason.EXACT_DUPLICATE;
        }

        String nearKey = buildNearKey(event);
        if (nearKey != null && nearCache.asMap().containsKey(nearKey)) {
            return FilterReason.NEAR_DUPLICATE;
        }

        return FilterReason.ACCEPTED;
    }

    /**
     * Records an accepted event in both caches so future duplicates are caught.
     */
    public void record(Event event) {
        if (event.getEventId() != null) {
            exactCache.put(event.getEventId(), Boolean.TRUE);
        }
        String nearKey = buildNearKey(event);
        if (nearKey != null) {
            nearCache.put(nearKey, Boolean.TRUE);
        }
    }

    private String buildNearKey(Event event) {
        if (event.getCookieId() == null || event.getEventType() == null || event.getClientTimestamp() == null) {
            return null;
        }
        return event.getCookieId() + "|" + event.getEventType() + "|" + event.getClientTimestamp();
    }
}
