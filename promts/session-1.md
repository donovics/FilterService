# Session 1 — Prompt History

---

**1.**
Build a service that expects a stream of user-interaction events, removes duplicates, flags bot traffic, and forwards the clean stream to a downstream queue. Make it in Java 17 and with Maven. It should listen to `http://localhost:8080/events` and expect data from the POST endpoint.

Event schema:
- `event_id` — string
- `event_type` — string (`view` / `visible` / `click`)
- `cookie_id` — string
- `client_timestamp` — ISO-8601 string
- `received_at` — ISO-8601 string
- `user_agent` — string
- `ip` — string
- `placement_id` — string
- `referrer` — string

Store the accepted events in an in-memory structure. In the output folder make 2 files, `shipped_events.ndjson` and `output/summary.md`.

---

**2.**
A bit too many bot generated event goes to Shipped. Make it so the service catches more of them.

---

**3.**
Revert the following changes: In 'Signal 4 — Timestamp plausibility' the two restrictions with client_timestamp, and the 'Signal 7 — IP cookie-churn' change.

---

**4.**
Also revert the latest 'Signal 3 — UA format validation' change.

---

**5.**
The bot filtering is a little bit too strict. Make it just a tiny bit looser. In the summary.md the two duplicate value should be summed and written in one 'duplicates' row.

---

**6.**
Good work! Now create necessary tests.

---

**7.**
Edit bot filtering again to allow a bit more to be shipped.

---

**8.**
Revert last change from latest bot filtering request.

---

**9.**
The project was reverted to a previous commit. Re-read it and add one more constraint to bot filtering.

---

**10.**
Remove 'Cookie event-rate limit' from bot filtering logic.

---

**11.**
CRITICAL: Respond with TEXT ONLY. Do NOT call any tools. Summarize the current state of the project: what files exist, what each service does, what the bot-detection signals are, and what the pipeline order is.

---

**12.**
Sorry it was a bad idea. Put 'Cookie event-rate limit' back.

---

**13.**
Write necessary test.

---

**14.**
Export this session's promt history into promts/session-1.md .

---

**15.**
/init — Analyze this codebase and create a CLAUDE.md file.

---

**16.**
Refresh promts/session-1.md .
