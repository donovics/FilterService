package org.jztrmnkl.filterservice.service;

import org.jztrmnkl.filterservice.model.Event;
import org.jztrmnkl.filterservice.model.FilterReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DeduplicationService}.
 *
 * Key invariants verified:
 * - {@code classify()} is read-only: calling it never modifies the caches.
 * - {@code record()} must be called explicitly before a duplicate can be detected.
 * - Null event_id or null composite-key fields are handled gracefully (no NPE, no
 *   spurious matches).
 */
class DeduplicationServiceTest {

    private DeduplicationService service;

    @BeforeEach
    void setUp() {
        service = new DeduplicationService();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static Event event(String eventId, String cookieId, String eventType, String ts) {
        Event e = new Event();
        e.setEventId(eventId);
        e.setCookieId(cookieId);
        e.setEventType(eventType);
        e.setClientTimestamp(ts);
        return e;
    }

    // =========================================================================
    // First-seen events are accepted
    // =========================================================================

    @Test
    void firstEvent_isClassifiedAsAccepted() {
        Event e = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");
        assertThat(service.classify(e)).isEqualTo(FilterReason.ACCEPTED);
    }

    // =========================================================================
    // Exact duplicate (same event_id)
    // =========================================================================

    @Test
    void sameEventId_afterRecord_isExactDuplicate() {
        Event e = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");
        service.record(e);

        assertThat(service.classify(e)).isEqualTo(FilterReason.EXACT_DUPLICATE);
    }

    @Test
    void sameEventId_withDifferentPayload_isStillExactDuplicate() {
        Event first  = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");
        Event second = event("evt-1", "cid-2", "view",  "2024-01-15T11:00:00Z");
        service.record(first);

        // Exact-dup check (event_id match) fires before the near-dup check
        assertThat(service.classify(second)).isEqualTo(FilterReason.EXACT_DUPLICATE);
    }

    // =========================================================================
    // Near duplicate (same composite key, different event_id)
    // =========================================================================

    @Test
    void differentEventId_sameCompositeKey_afterRecord_isNearDuplicate() {
        Event first  = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");
        Event second = event("evt-2", "cid-1", "click", "2024-01-15T10:00:00Z");
        service.record(first);

        assertThat(service.classify(second)).isEqualTo(FilterReason.NEAR_DUPLICATE);
    }

    // =========================================================================
    // Different events are accepted
    // =========================================================================

    @Test
    void differentCookieId_afterRecord_isAccepted() {
        Event first  = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");
        Event second = event("evt-2", "cid-2", "click", "2024-01-15T10:00:00Z");
        service.record(first);

        assertThat(service.classify(second)).isEqualTo(FilterReason.ACCEPTED);
    }

    @Test
    void differentEventType_afterRecord_isAccepted() {
        Event first  = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");
        Event second = event("evt-2", "cid-1", "view",  "2024-01-15T10:00:00Z");
        service.record(first);

        assertThat(service.classify(second)).isEqualTo(FilterReason.ACCEPTED);
    }

    @Test
    void differentClientTimestamp_afterRecord_isAccepted() {
        Event first  = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");
        Event second = event("evt-2", "cid-1", "click", "2024-01-15T10:00:05Z");
        service.record(first);

        assertThat(service.classify(second)).isEqualTo(FilterReason.ACCEPTED);
    }

    // =========================================================================
    // classify() is read-only
    // =========================================================================

    @Test
    void classify_withoutRecord_doesNotPolluteCaches() {
        Event e = event("evt-1", "cid-1", "click", "2024-01-15T10:00:00Z");

        // Two classify() calls without any record() — both must return ACCEPTED
        assertThat(service.classify(e)).isEqualTo(FilterReason.ACCEPTED);
        assertThat(service.classify(e)).isEqualTo(FilterReason.ACCEPTED);
    }

    // =========================================================================
    // Null-safety
    // =========================================================================

    @Test
    void nullEventId_skipsExactDupCheck_noException() {
        Event e1 = event(null, "cid-1", "click", "2024-01-15T10:00:00Z");
        service.record(e1);

        // A second event with null event_id and different near-key must be ACCEPTED,
        // not incorrectly treated as an exact duplicate
        Event e2 = event(null, "cid-2", "view", "2024-01-15T11:00:00Z");
        assertThat(service.classify(e2)).isEqualTo(FilterReason.ACCEPTED);
    }

    @Test
    void nullCookieId_skipsNearDupCheck_noException() {
        Event e1 = event("evt-1", null, "click", "2024-01-15T10:00:00Z");
        service.record(e1);

        // Without a near-key, the second event (different event_id) must be ACCEPTED
        Event e2 = event("evt-2", null, "click", "2024-01-15T10:00:00Z");
        assertThat(service.classify(e2)).isEqualTo(FilterReason.ACCEPTED);
    }

    @Test
    void nullEventType_skipsNearDupCheck_noException() {
        Event e1 = event("evt-1", "cid-1", null, "2024-01-15T10:00:00Z");
        service.record(e1);

        Event e2 = event("evt-2", "cid-1", null, "2024-01-15T10:00:00Z");
        assertThat(service.classify(e2)).isEqualTo(FilterReason.ACCEPTED);
    }
}
