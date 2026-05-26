# Expected Output — Rough Shape

This file describes the approximate shape of the output you should see when
your service runs against `data/events.ndjson`. The numbers are intentionally
fuzzy: your exact counts will depend on the deduplication window and
bot-detection heuristics you choose. The goal here is to give you a
sanity-check, not a strict answer key.

## Approximate breakdown

- **Total events received:** ~10,098
- **Dropped as duplicates:** ~1,339 (±150 depending on your deduplication window)
- **Flagged as bots:** ~1,111 (±200 depending on which bot signals you use)
- **Shipped to queue:** ~7,648 (±200)

If your shipped count is wildly different from this — for example, if you
ship fewer than 6,793 or more than 8,135 — something is probably
off and worth investigating.

## What's in the noise

The sample data contains:

- **Exact duplicates** — the same `event_id` arrives more than once. These
  are unambiguous and any reasonable approach should catch them.
- **Near-duplicates** — the same `cookie_id` + `event_type` + `client_timestamp`
  arrives with a *different* `event_id` a few seconds later. These look like
  client-side retries that got new IDs from the collector. Catching these
  is one of the more interesting parts of the task.
- **Boundary cases** — a small number of near-duplicates arrive 30 to 90
  seconds apart. Whether you treat these as duplicates depends on the
  window you choose. Both wider and narrower windows are defensible — what
  matters is that your choice is deliberate and you can explain it.
- **Legitimate repeat traffic** — some cookies legitimately view the same
  placement multiple times, minutes apart. These should NOT be deduped,
  even though the `cookie_id` and `placement_id` match.
- **Bot traffic** — a handful of cookies behave in ways no real user could.
  We will not tell you what to look for, but the signals are present in the
  data if you go looking.

## Why we share this

We share these rough numbers because we want you to focus on the design and
the judgment, not on guessing whether your solution is roughly working.
If your numbers are within these ranges, you're in good shape. If they're
not, treat that as a debugging signal.
