# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

All Maven commands run from the project root.

```bash
mvn clean compile          # compile only
mvn test                   # run all tests
mvn test -Dtest=ClassName  # run one test class
mvn test -Dtest=ClassName#methodName  # run one test method
mvn package -DskipTests    # build JAR without running tests
mvn spring-boot:run        # start the service on port 8080
```

The Surefire plugin is configured with `-Dnet.bytebuddy.experimental=true` because the runtime JVM may be newer than the ByteBuddy version bundled in Spring Boot 3.2.x. Do not remove that flag.

## Architecture

**FilterService** is a Spring Boot 3.2.5 / Java 17 HTTP service. It receives user-interaction events on `POST /events` (single) and `POST /events/batch` (array), filters them, and writes results to `output/`.

### Pipeline order — critical

```
Request → BotDetectionService → DeduplicationService → accept
```

Bot detection runs **before** deduplication deliberately: bot `event_id`s must never enter the dedup caches, otherwise a bot could poison a legitimate user's dedup slot for the next 10 minutes.

### Key design decisions

**`DeduplicationService`** separates read from write:
- `classify(event)` — read-only; returns `ACCEPTED / EXACT_DUPLICATE / NEAR_DUPLICATE` without touching the caches.
- `record(event)` — writes to both caches. Called only after a final ACCEPTED decision.

Two cache tiers:
- **Exact dup**: `event_id` → 10-minute TTL Caffeine cache.
- **Near dup**: composite key `cookie_id|event_type|client_timestamp` → 60-second TTL Caffeine cache. Key is `null` (and the check is skipped) when any of the three fields is null.

**`BotDetectionService`** runs six independent signals; any single positive rejects the event:
1. Null / blank user-agent
2. UA matches compiled bot/crawler/library/headless-framework patterns
3. Missing or unparseable `client_timestamp` / `received_at` (must be valid ISO-8601 instants)
4. IP rate > 90 events / 60 s (Caffeine cache, resets on expiry)
5. Cookie rate > 20 events / 10 s (Caffeine cache, resets on expiry)
6. `event_type` not in `{view, visible, click}`

**Jackson is configured globally for `SNAKE_CASE`** (`spring.jackson.property-naming-strategy=SNAKE_CASE` in `application.properties`). Java camelCase fields on `Event` (e.g. `eventId`, `userAgent`) automatically serialize/deserialize as `event_id`, `user_agent`, etc. Do not add `@JsonProperty` annotations — the global strategy handles it.

**`OutputService`** writes two files:
- `output/shipped_events.ndjson` — one JSON line per accepted event, appended.
- `output/summary.md` — full processing report, overwritten on every event.

The output directory is configurable via `app.output-dir` (defaults to `output`). Tests set it to `target/test-output` through `src/test/resources/application.properties`.

### Expected output against `data/events.ndjson`

~10,098 events received → ~1,339 duplicates, ~1,111 bots, ~7,648 shipped. Counts outside the range 6,793–8,135 shipped indicate a regression in filtering logic.
