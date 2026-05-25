# FilterService — Processing Summary

_Last updated: 2026-05-25T20:59:53.588895500Z_

## Event Counts

| Metric              | Count  |
|---------------------|--------|
| Received            |  10098 |
| Exact duplicates    |    540 |
| Near-duplicates     |    899 |
| Bot traffic         |    730 |
| **Shipped**         | **7929** |

Ship rate: 78,5% of received events forwarded to the downstream queue.

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
   Caffeine expiry. Any IP that exceeds **120 events per minute** is flagged.
   Real users do not generate that volume from a single address.

4. **Cookie event-rate limit** — a per-cookie counter resets every 10 seconds.
   Any cookie that exceeds **30 events per 10 seconds** is flagged.
   That rate is faster than any human interaction pattern.

---

_Total filtered (dups + bots): 2169 / 10098 received_
