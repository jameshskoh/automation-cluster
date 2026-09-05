# Draft notes (pre-service-architect)

See the `draft-notes.md` convention in the root `AGENTS.md`. Detail here was moved out of the
system-level `docs/architecture.md` during the article-summary workflow design (2026-09-05), for
the `@service-architect` pass that reopens `claude-automator`'s design (see `docs/PIPELINE.md`) to
pick up when it turns multi-use-case dispatch into an actual implementation.

## Multi-use-case dispatch — code-shape notes

The system-level requirement (see `../../docs/architecture.md` "claude-automator: multi-use-case
dispatch"): one running instance, owning one Pub/Sub subscription per registered use case, needs a
way to map each inbound message to a prompt template and an outbound `use_case`/`stage` to publish.

### Concurrency shape as originally sketched

One poll loop per owned subscription, each independently pulling/acking/nacking against its own
subscription, all within the same process. This is a strawman, not a decision — how N loops
actually share the process (async loop per subscription vs. some other concurrency structure)
without regressing the existing single-loop `POLL_COUNT`/`POLL_INTERVAL_MS` behavior QA relies on
today is genuinely open; see `docs/backlog.md`'s claude-automator entry.

### Registry shape as originally sketched

A per-use-case registry entry, one per registered use case, illustrated as this table — field names
and shape are a starting proposal, not fixed; the concrete config format (static JSON/YAML vs.
code-defined table) is this service's own call:

| Field | Purpose |
|---|---|
| `use_case` | The `use_case` value this entry handles (e.g. `QA`, `ARTICLE_SUMMARY`). |
| `inbound_subscription_id` | The Pub/Sub subscription this entry polls (one of claude-automator's several owned subscriptions). |
| `inbound_stage` | The `stage` value expected on inbound messages from that subscription (e.g. `ASKED`, `SUMMARIZE_REQUESTED`) — validated the same way the current hardcoded check works, just parameterized per entry instead of a single literal. |
| `prompt_template` | How to turn the inbound message's `payload`/`metadata` into the task text handed to the agent (e.g. `QA` hands the question through near-verbatim today; `ARTICLE_SUMMARY` prepends a fixed "summarize" instruction ahead of the article content — see `../../docs/use-cases/article-summary.md`). |
| `outbound_topic_id` | Where to publish the result. Expected to be the same value (`claude-automator-responses`) across every entry, but kept as an explicit field rather than a hardcoded global — a future use case is free to prove that assumption wrong without a registry shape change. |
| `outbound_stage` | The `stage` value to stamp on the outbound message (e.g. `ANSWERED`, `SUMMARIZED`). |

Dispatch: the poll loop that received a message already knows which subscription (hence which
registry entry) it came from, so the entry directly supplies the prompt template and the outbound
`use_case`/`stage`/topic — no content-based routing of the message body needed. `QA` becomes the
first row of this registry, byte-for-byte equivalent to its current hardcoded behavior (today's
hardcoded shape: a single `PUBSUB_TOPIC_ID`/`PUBSUB_SUBSCRIPTION_ID` pair, plus module-level
`QA_USE_CASE`/`ASKED_STAGE`/`ANSWERED_STAGE` constants baked into both the inbound validator and the
outbound publish).
