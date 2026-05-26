package org.jztrmnkl.filterservice.controller;

import org.jztrmnkl.filterservice.model.FilterReason;
import org.jztrmnkl.filterservice.service.EventProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests for {@link EventController}.
 *
 * Only the controller bean is loaded; {@link EventProcessingService} is
 * replaced with a Mockito mock so no real pipeline logic executes.
 *
 * The test {@code application.properties} configures {@code SNAKE_CASE}
 * Jackson naming, so request bodies must use snake_case field names and
 * response assertions must also use snake_case keys.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventProcessingService processingService;

    // -------------------------------------------------------------------------
    // Minimal valid event payload (snake_case, as the app expects)
    // -------------------------------------------------------------------------

    private static final String EVENT_JSON = """
            {
              "event_id":         "evt-001",
              "event_type":       "click",
              "cookie_id":        "cid-abc",
              "client_timestamp": "2024-01-15T10:00:00Z",
              "received_at":      "2024-01-15T10:00:00.123Z",
              "user_agent":       "Mozilla/5.0",
              "ip":               "203.0.113.1",
              "placement_id":     "p1",
              "referrer":         "https://example.com"
            }
            """;

    // =========================================================================
    // POST /events — single event
    // =========================================================================

    @Test
    void acceptedEvent_returns200WithShippedTrue() throws Exception {
        when(processingService.process(any())).thenReturn(FilterReason.ACCEPTED);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value("evt-001"))
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.shipped").value(true));
    }

    @Test
    void botEvent_returns200WithShippedFalse() throws Exception {
        when(processingService.process(any())).thenReturn(FilterReason.BOT_TRAFFIC);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("bot_traffic"))
                .andExpect(jsonPath("$.shipped").value(false));
    }

    @Test
    void exactDuplicateEvent_returns200WithShippedFalse() throws Exception {
        when(processingService.process(any())).thenReturn(FilterReason.EXACT_DUPLICATE);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("exact_duplicate"))
                .andExpect(jsonPath("$.shipped").value(false));
    }

    @Test
    void nearDuplicateEvent_returns200WithShippedFalse() throws Exception {
        when(processingService.process(any())).thenReturn(FilterReason.NEAR_DUPLICATE);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("near_duplicate"))
                .andExpect(jsonPath("$.shipped").value(false));
    }

    // =========================================================================
    // POST /events/batch
    // =========================================================================

    @Test
    void batchEndpoint_returnsProcessedCountAndBreakdown() throws Exception {
        // Three events: accepted, bot, accepted
        when(processingService.process(any()))
                .thenReturn(FilterReason.ACCEPTED)
                .thenReturn(FilterReason.BOT_TRAFFIC)
                .thenReturn(FilterReason.ACCEPTED);

        String batchJson = "[" + EVENT_JSON + "," + EVENT_JSON + "," + EVENT_JSON + "]";

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3))
                .andExpect(jsonPath("$.breakdown.accepted").value(2))
                .andExpect(jsonPath("$.breakdown.bot_traffic").value(1));
    }

    @Test
    void batchEndpoint_singleEvent_returnsCorrectBreakdown() throws Exception {
        when(processingService.process(any())).thenReturn(FilterReason.NEAR_DUPLICATE);

        String batchJson = "[" + EVENT_JSON + "]";

        mockMvc.perform(post("/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.breakdown.near_duplicate").value(1));
    }
}
