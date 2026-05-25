package org.jztrmnkl.filterservice.controller;

import org.jztrmnkl.filterservice.model.Event;
import org.jztrmnkl.filterservice.model.FilterReason;
import org.jztrmnkl.filterservice.service.EventProcessingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventProcessingService processingService;

    public EventController(EventProcessingService processingService) {
        this.processingService = processingService;
    }

    /**
     * Accepts a single event.
     * Returns 200 with the filter outcome; never 4xx for bot/dup events
     * so that upstream retries don't pile up on error status codes.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> receiveEvent(@RequestBody Event event) {
        FilterReason result = processingService.process(event);
        return ResponseEntity.ok(buildResponse(event.getEventId(), result));
    }

    /**
     * Accepts a batch of events in one request.
     */
    @PostMapping(value = "/batch",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> receiveEvents(@RequestBody List<Event> events) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Event event : events) {
            FilterReason result = processingService.process(event);
            breakdown.merge(result.name().toLowerCase(), 1L, Long::sum);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("processed", (long) events.size());
        response.put("breakdown", breakdown);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildResponse(String eventId, FilterReason reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event_id", eventId);
        body.put("status", reason.name().toLowerCase());
        body.put("shipped", reason == FilterReason.ACCEPTED);
        return body;
    }
}
