# FilterService — Processing Summary

_Last updated: 2026-05-25T22:25:59.525626200Z_

## Event Counts

| Metric              | Count  |
|---------------------|--------|
| Received            |  10098 |
| Duplicates          |   1410 |
| Bot traffic         |   1041 |
| **Shipped**         | **7647** |

Ship rate: 75,7% of received events forwarded to the downstream queue.

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

Five independent signals are evaluated; any single positive rejects the event.
Bot events are counted but never forwarded and never recorded in the
deduplication caches.

1. **Missing / blank user-agent** — every real browser sends a non-empty UA
   string.  Events without one cannot originate from a real browser.

2. **User-agent keyword patterns** — the UA is tested against compiled regexes
   covering: generic automation keywords (`bot`, `crawler`, `spider`, `scraper`,
   `slurp`, `probe`, `scan`); common HTTP libraries (`curl`, `wget`,
   `python-requests`, `httpx`, `aiohttp`, `scrapy`, `okhttp`, `go-http-client`,
   `mechanize`, `urllib3`, …); named crawlers (Googlebot, Bingbot, AhrefsBot,
   SemrushBot, Twitterbot, …); and headless / automation frameworks
   (HeadlessChrome, PhantomJS, Selenium, Puppeteer, Playwright, Cypress, …).

3. **Timestamp presence and parseability** — both `client_timestamp` and
   `received_at` must be present and parse as valid ISO-8601 instants.
   Events with missing or malformed timestamp fields are rejected, as real
   collectors always produce well-formed timestamps.

4. **IP event-rate limit** — a per-IP counter resets every 60 seconds. Any IP
   that exceeds **90 events per minute** is flagged; no real end-user generates
   that volume from a single device.

5. **Cookie event-rate limit** — a per-cookie counter resets every 10 seconds.
   Any cookie that exceeds **20 events per 10 seconds** is flagged; that pace
   is faster than any human interaction.

---

_Total filtered (dups + bots): 2451 / 10098 received_
