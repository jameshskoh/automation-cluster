---
status: DRAFT
---

# gateway-svc architecture

The concrete, as-built design of the **gateway** — the system's one always-on, stateful service. It
receives inbound requests (Slack, HTTP), assigns a `request_id`, publishes the first message of a use
case, and delivers the eventual answer back to the caller.

Service-level layer only — it does **not** restate the system model. Read these first; they stay
authoritative for anything not specific to how the gateway is built:

- Abstract gateway model (registry / timeout / error-short-circuit / graceful-shutdown):
  [`../../docs/architecture.md`](../../docs/architecture.md) "The gateway".
- Message envelope + publish-time schema enforcement: [`../../docs/arch/messaging.md`](../../docs/arch/messaging.md).
- Topic / subscription / DLQ naming + provisioning: [`../../docs/arch/topics-and-provisioning.md`](../../docs/arch/topics-and-provisioning.md).
- Module / package / naming / build rules: [`../AGENTS.md`](../AGENTS.md).
- Cross-service flows: [`../../docs/use-cases/`](../../docs/use-cases/); gateway-side subsets are
  [`use-cases/qa.md`](use-cases/qa.md) and [`use-cases/weather.md`](use-cases/weather.md).

## Module layout

Clean-architecture Maven modules; module/package/build rules in [`../AGENTS.md`](../AGENTS.md). Which
carry code today:

| Module | Populated | Holds |
|---|---|---|
| `gateway-domain` | yes | `GatewayMessage` envelope record (self-validating) |
| `gateway-application` | yes | `PendingAnswerRegistry`, `AnswerSink`, inbound use cases (`AsyncQuestion*`, `AsyncAnswer*`), `PublishGatewayMessagePort` |
| `gateway-controller` | yes | Slack Socket Mode (`SlackAppConfig`), HTTP long-poll (`AsyncQuestionAnswerController`), `PendingAnswerSweeper` |
| `gateway-consumer` | yes | `Subscriber` config + `GatewayResponseSubscriber` |
| `gateway-producer` | yes | `PubSubGatewayMessagePublisher` (single `Publisher`) |
| `gateway-bootstrap` | yes | `GatewayApplication`, `UseCaseConfig` wiring, `application.yaml` |
| `gateway-common` | stub | — |
| `gateway-scheduler`, `gateway-repository`, `gateway-client` | stub | empty (no scheduler/persistence/outbound-HTTP need yet) |

## Inbound adapters

Two transports, both wrapping delivery as an `AnswerSink` so the correlation core stays
framework-neutral:

- **Slack (Socket Mode)** — `SlackAppConfig`. `/ask <question>` acks within 3s ("On it…") then
  delivers via the caller's `response_url` (valid ~30 min / 5 uses). `/hello`, `/hello-async` are
  demos, not production use cases.
- **HTTP long-poll** — `AsyncQuestionAnswerController`, `POST /qa/async`. Holds the connection via a
  `DeferredResult`; returns the answer on arrival or `504` on timeout.

## Callback correlation (in-memory registry)

`PendingAnswerRegistry` maps `request_id → Pending{sink, registeredAt}` in a `ConcurrentHashMap`.
`AnswerSink` is a framework-free delivery target with `deliver(message)` and a default-no-op
`onExpire()` (Slack overrides `onExpire` to post a timeout notice while `response_url` is still valid;
the HTTP path has already returned `504`).

- **Register before publish** — every inbound path registers the sink before publishing, so a fast
  round-trip can't complete before the sink exists.
- **`complete(request_id, message)`** — removes the entry and invokes `sink.deliver`; the single
  delivery path for **both** a normal answer and a terminal error (see "Error short-circuit").
- **Unknown / already-evicted `request_id`** — `complete` logs a warning and drops the message (the
  accepted stale-redelivery / single-instance gap from the system design).

In-memory only (a hard crash loses in-flight registrations); distributed/persistent handling is
deferred — see [`../../docs/backlog.md`](../../docs/backlog.md).

## Outbound publisher

`PubSubGatewayMessagePublisher` owns a single `Publisher` bean targeting the gateway's one outbound
topic `gateway-requests`. It sets `use_case`/`stage` as Pub/Sub **attributes** (subscription filters
select on attributes) as well as body fields, and publishes non-blocking.

> **As-built divergence (documented, deferred).** On a publish failure the code only logs;
> [`../../docs/arch/messaging.md`](../../docs/arch/messaging.md) requires the gateway to *synchronously*
> fail the request (there is no upstream DLQ for the gateway's own publish), so today such a request
> instead falls through to the timeout.

## Response consumer

`GatewayResponseSubscriber` (a `MessageReceiver`) deserializes the envelope, drives the inbound use
case, and acks; on any failure it nacks → redelivery → dead-letter (never a silent drop). Wired by
`PubSubSubscriberConfig`.

Today **one** subscription: `gateway-claude-automator-responses-sub` on `claude-automator-responses`,
filtered `QA AND ANSWERED`, driving `AsyncAnswerUseCase` → `registry.complete`. WEATHER generalizes
this to multiple subscriptions (see [`use-cases/weather.md`](use-cases/weather.md) and the task
breakdown).

## Timeout & liveness

**Target (per-use-case timeout).** Every use case declares one timeout under
`gateway.use-cases.<NAME>.timeout-millis`; the registry stamps each entry with
`expiresAt = registeredAt + timeout`; the sweeper fires `onExpire` and evicts once `expiresAt` passes;
the HTTP long-poll reads the same value for its `DeferredResult`. One key per use case is the single
source of truth for every transport.

**As-built baseline.** Not yet per-use-case — two QA-hardcoded mechanisms approximate it:

- `AsyncQuestionAnswerController` — per-request `DeferredResult(qa.async.timeout-millis = 300000)` → `504`.
- `PendingAnswerSweeper` — a **global** TTL sweep on `qa.pending.ttl-millis = 1740000` (~29 min, kept
  under Slack's `response_url` window) calling `onExpire`.

No per-entry `expiresAt`, no synthesized-failure timer. Side effect: the Slack `/ask` `onExpire` notice
fires at ~29 min, not the declared 5-min timeout; folding to the target aligns it — and is *required*
by WEATHER's Slack-only 2-min flow, which has no `DeferredResult` fallback. Tracked in
[`../../docs/backlog.md`](../../docs/backlog.md), built in the task breakdown.

## Graceful shutdown

**Not implemented.** The system design's drain → force-fail → forced-shutdown sequence
([`../../docs/architecture.md`](../../docs/architecture.md) "Graceful shutdown") is the target; today
only the periodic TTL sweeper exists (eviction, not a shutdown hook). Deferred
([`../../docs/backlog.md`](../../docs/backlog.md)); out of scope for the WEATHER pass.

## Error short-circuit (gateway side)

**Not implemented today** (no `FAILED` subscription or handler). Target
([`../../docs/architecture.md`](../../docs/architecture.md) "Error short-circuit"): a terminal error
stage carrying `request_id` + reason arrives on a gateway-owned filtered subscription; the gateway
resolves the entry, delivers the failure through the **same `registry.complete` / `AnswerSink.deliver`
path** as a normal answer (reason in `metadata`), and evicts. WEATHER is the first adopter
(weather-svc's `WEATHER/FAILED`) — see the task breakdown.

## Configuration & secrets

- `application.yaml`: server port `8081`; `qa.async.timeout-millis`, `qa.pending.ttl-millis`,
  `qa.pending.sweep-interval-millis` (to be superseded by `gateway.use-cases.*`); Pub/Sub project /
  topic / subscription ids from env vars.
- Secrets via Spring Cloud GCP Secret Manager (`sm@`): `slack-app-token`, `slack-bot-token`.

## Provisioning

`gateway-svc/scripts/provision-pubsub.sh` provisions only what the gateway owns — the `gateway-requests`
topic and the DLQ(s) + subscription(s) it consumes — sourcing `scripts/pubsub-lib.sh`.
Ownership/naming/run-order (including the cross-service dependency cycle WEATHER introduces) live in
[`../../docs/arch/topics-and-provisioning.md`](../../docs/arch/topics-and-provisioning.md).

## As-built divergences from the system design

Each is a tracked deferral in [`../../docs/backlog.md`](../../docs/backlog.md), not fixed this pass:

| Divergence | Section |
|---|---|
| Publish failure only logs; must synchronously fail the request | Outbound publisher |
| Timeout is flat `qa.*`, not per-use-case; no per-entry `expiresAt`; Slack `onExpire` fires at ~29 min not the declared timeout | Timeout & liveness |
| Graceful-shutdown drain/force-fail/forced-shutdown not implemented (only the TTL sweeper) | Graceful shutdown |
| Error short-circuit (gateway-side `FAILED` handling) not implemented | Error short-circuit |

The last two are addressed (short-circuit) or left deferred (shutdown) per their sections.

## Task breakdown (WEATHER)

Implementable units for WEATHER phase-3; each lists the files/dirs it owns so parallel implementers
don't collide. **T3, T4, T5 all reach into `gateway-application`; boundaries are drawn below.** T5
lands before (or with) T3/T4, which consume its registry changes.

- **T1 — WEATHER Slack command.**
  Owns: `adapters/in/gateway-controller/` (extend `SlackAppConfig` or add a command class).
  Accept: `/get-weather <city>, <state>` parses the comma-delimited city/state (city names contain
  spaces), acks within 3s, registers an `AnswerSink` delivering via `response_url`, and calls T2.

- **T2 — WEATHER request publisher (parametric).**
  Owns: new inbound service in `gateway-application/.../application/in/` + its bean in
  `gateway-bootstrap/UseCaseConfig.java`. Generalize the request path into **one parametric request
  service** (use_case + stage + content), not a WEATHER-specific class.
  Accept: publishes `WEATHER/REQUESTED` on `gateway-requests` with location in `payload`, `metadata`
  empty; registers the sink before publishing.

- **T3 — WEATHER inbound subscriptions.**
  Owns (consumer): `adapters/in/gateway-consumer/` — generalize `PubSubSubscriberConfig` +
  `GatewayResponsePubSubProperties` from a single subscription to a **list**, and add
  `gateway-claude-automator-responses-weather-sub` (`WEATHER AND ANSWERED`) and
  `gateway-weather-svc-results-sub` (`WEATHER AND FAILED`).
  Owns (application boundary): routing `ANSWERED` → answer path, `FAILED` → T4. Does **not** touch
  `PendingAnswerRegistry` (T5) or the FAILED delivery logic (T4).
  Accept: both subscriptions consume with correct filters; nack → DLQ on failure.

- **T4 — Generic error-stage handler.**
  Owns (application boundary): terminal-`FAILED` handling in `gateway-application` — resolve
  `request_id`, deliver the reason via the existing `registry.complete` / `AnswerSink.deliver` path
  (**no new `sink.fail()`**), evict. Extends `AsyncAnswerService` (or a sibling); does **not** own
  consumer wiring (T3) or registry internals (T5).
  Accept: a `WEATHER/FAILED` fails the caller with the reason before the timeout elapses.

- **T5 — Per-use-case timeout generalization.**
  Owns: `PendingAnswerRegistry` (`gateway-application`, add per-entry `expiresAt`),
  `PendingAnswerSweeper` (`gateway-controller`), and the `gateway.use-cases.<NAME>.timeout-millis`
  config + wiring in `gateway-bootstrap`, superseding the flat `qa.*` keys. T3/T4 depend on but don't
  modify these.
  Accept: QA (300000) and WEATHER (120000) coexist via per-use-case config; the sweeper evicts and
  fires `onExpire` at each entry's own `expiresAt`.

- **T6 — Provisioning.**
  Owns: `gateway-svc/scripts/provision-pubsub.sh`. Add the two WEATHER subscriptions + DLQs
  (`claude-automator-responses-gateway-weather-sub-dlq`, `weather-svc-results-gateway-sub-dlq`);
  document the two-pass run order (`weather-svc-results` first) per
  [`../../docs/use-cases/weather.md`](../../docs/use-cases/weather.md).
