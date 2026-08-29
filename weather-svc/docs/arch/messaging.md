---
status: ACCEPTED
---

# Messaging: trigger, envelope mapping, error → ack/nack

How weather-svc receives and emits messages on Pub/Sub. Builds on the shared contract in
[`../../docs/arch/messaging.md`](../../docs/arch/messaging.md) (the `GatewayMessage` envelope,
attributes-vs-body, publish-time schema validation) and the wiring in
[`../../docs/arch/topics-and-provisioning.md`](../../docs/arch/topics-and-provisioning.md). See
[`../architecture.md`](../architecture.md) for the runtime.

## Trigger: Pub/Sub push → HttpFunction

A Pub/Sub **push** subscription POSTs to the Cloud Run function's HTTP endpoint; weather-svc implements
**`HttpFunction`** and parses the push envelope manually. (Why push, not Eventarc/`CloudEventsFunction`:
[`../architecture.md`](../architecture.md), "Trigger".)

### Inbound push envelope

JSON of the shape (fields weather-svc reads; others ignored):

```json
{
  "message": {
    "data": "<base64 of the GatewayMessage JSON body>",
    "messageId": "...",
    "attributes": { "use_case": "WEATHER", "stage": "REQUESTED", "request_id": "<uuid>" }
  },
  "subscription": "projects/<proj>/subscriptions/weather-svc-gateway-requests-sub"
}
```

- `message.data` base64-decodes to the `GatewayMessage` JSON (`use_case`, `stage`, `request_id`,
  `payload`, `metadata`).
- `message.attributes` carries `use_case`/`stage`/`request_id` too (per the shared contract). The
  subscription filter already guarantees `WEATHER`/`REQUESTED`, so the handler keys off the decoded body
  and echoes `request_id`.

### Inbound `payload` contract (external — not designed here)

`REQUESTED.payload` is a JSON object describing the location. weather-svc **consumes** this shape; the
gateway's phase-3 work **produces** it:

```json
{ "city": "Ayer Hitam", "state": "Johor" }
```

`state` drives the client-side `admin1` filter (see
[`open-meteo-integration.md`](open-meteo-integration.md)). A body that fails to decode into this shape
is a bad request → terminal `FAILED`.

## Outbound messages

weather-svc owns one topic, **`weather-svc-results`**, and publishes both outcomes to it (one topic per
publisher; the two consumer subscriptions filter by `stage`). Every publish sets `use_case`/`stage` in
**both** the Pub/Sub attributes (for filtering) and the body (for logging/consumers), echoes the
originating `request_id`, and is JSON validated at publish time against the shared `gateway-message`
schema.

### `WEATHER`/`FETCHED` (success → claude-automator)

- `payload`: the **aggregated weather JSON** weather-svc produces — resolved location (name, state,
  coordinates, timezone) plus the four blocks (midnight/morning/afternoon/night), each with temp
  high/low, feels-like high/low, raining probability, and sky condition (description **and** emoji).
  This is derived JSON, not open-meteo's raw hourly arrays — deterministic bucketing/arithmetic is
  weather-svc's job; language generation is claude's.
- `metadata`: the **interpretation prompt** (below).

### `WEATHER`/`FAILED` (terminal error → gateway)

- `metadata`: a human-readable reason (e.g. `no match for "Ayer Hitam" in "Melaka", Malaysia`, or
  `weather service temporarily unavailable`).
- `payload`: empty.

## Interpretation prompt (`FETCHED.metadata`)

weather-svc authors the prompt; claude-automator concatenates it with the `payload` data using an
XML-tag delimiter — that concatenation is **claude-automator's** side of the prompt/data interface
(external, not designed here). The prompt must instruct claude to produce:

1. **A 2-sentence summary** — (a) if and when it rains; (b) the feels-like temperature high/low.
2. **A compact table**, one row per block (midnight / morning / afternoon / night), about three data
   columns beside the block label: temp high/low, feels-like high/low, and a **sky emoji**. The emoji
   is rendered from the sky classification weather-svc already supplies in `payload` (both description
   and emoji), so claude only formats — it does not classify weather.

The prompt must also tell claude to use **only** the supplied data (invent nothing) and keep prose
minimal. Exact wording is a phase-3 detail; the structure above is the contract.

## Error → ack/nack

As a push HTTP handler, weather-svc expresses ack/nack via the HTTP status it returns:

- **After a successful `FETCHED` or `FAILED` publish → 2xx** (Pub/Sub acks; the request is done —
  either the happy path continues to claude-automator or the gateway is failed fast).
- **Terminal failures are `FAILED` publishes, then 2xx** — no geocoding match, bad request, and (v1)
  transient open-meteo 5xx/network/timeout all publish `FAILED` and ack. v1 does **no in-process
  retry** on transient errors (deferred — [`../../docs/backlog.md`](../../docs/backlog.md)).
- **Return non-2xx only when weather-svc genuinely cannot publish** (outbound publish fails — e.g.
  schema violation — or the process crashes). Pub/Sub redelivers; after `MAX_DELIVERY_ATTEMPTS` the
  input lands in **`gateway-requests-weather-svc-sub-dlq`**, replayable (safe under the idempotency
  posture in [`../architecture.md`](../architecture.md)).

## Provisioning note (phase-3 task)

The shared `scripts/pubsub-lib.sh` `create_subscription_if_missing` creates **pull** subscriptions (no
`--push-endpoint`), which suits the gateway and claude-automator but not weather-svc's push trigger.
weather-svc's `scripts/provision-pubsub.sh` needs a **push variant** — a subscription created with
`--push-endpoint=<Cloud Run URL>`. Since that URL is only known after the first deploy, the push
subscription is provisioned **after** the initial deploy (or updated with the endpoint post-deploy).
Ownership, naming, filter, DLQ, and the two-pass cross-service run order are per
[`../../docs/use-cases/weather.md`](../../docs/use-cases/weather.md); weather-svc's script creates the
DLQ for its own subscription (`gateway-requests-weather-svc-sub-dlq`), per that doc's "Provisioned by"
column (following the subscription-owns-its-DLQ convention — a deliberate divergence from the QA
precedent where the gateway created the consumer's DLQ). Full task detail is T7 in
[`../architecture.md`](../architecture.md).
