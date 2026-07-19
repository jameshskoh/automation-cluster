---
status: ACCEPTED
---

# Architecture

## Overview

This repo is a mono-repo of independently deployable services that together process requests
asynchronously via GCP Pub/Sub. There is exactly one always-on, stateful service (the gateway);
everything else is event-driven and, other than the gateway, expected to be deployable as a
Cloud Run Function.

```mermaid
flowchart TD
    client["client (HTTP/WS)"]
    gateway["gateway<br/>(always-on, stateful)"]
    gwTopic["gateway's topic"]
    xxxsvc["xxxsvc (function)"]
    otherGcp["other GCP services"]
    nextTopic["xxxsvc's own topic"]
    nextStage["next stage consumer<br/>(chain continues until a terminal<br/>stage publishes back to the gateway)"]

    client -- request --> gateway
    gateway -- response --> client
    gateway -- publishes --> gwTopic
    gwTopic -- "filtered by use_case+stage attributes" --> xxxsvc
    xxxsvc <-- "publishes/reads" --> otherGcp
    xxxsvc -- publishes --> nextTopic
    nextTopic --> nextStage
    nextStage -. "eventually" .-> gateway
```

- **gateway** (`gateway-svc`): the only always-online service. Accepts
  inbound requests (HTTP, WebSocket, Slack, etc.), assigns a `request_id` (UUID), and is
  responsible for eventually delivering a response back to whichever caller made the original
  request — regardless of what form that caller's connection took.
- **functions** (`xxxsvc`): each is a Cloud Run Function that reads from one Pub/Sub subscription
  and (optionally) writes to one or more Pub/Sub topics. Side effects are allowed and expected
  inside a function (see "Side effects in functions" below).
- **claude-automator**: a special, non-FaaS event-driven service (long-running Node/TS process),
  not deployed as a Cloud Run Function. It participates in the same message-based flow as any
  other consumer/publisher.
- **other GCP services**: a catch-all box that the gateway and any function may depend on for
  side effects (Firestore, Cloud Storage, Secret Manager, Cloud Tasks, etc.) — omitted from
  individual sequence diagrams to avoid clutter; see "Side effects in functions."

All request processing beyond the gateway's initial receipt is asynchronous: the gateway
publishes a message, and the response — whenever and via however many intermediate stages it
takes — eventually arrives back at the gateway as another message, keyed by `request_id`.

## The gateway

- The only stateful service in the system, and the only one required to be always-online (needed
  to support WebSocket and long-poll connections).
- On receiving a request, generates a `request_id` (UUID) and registers it in an in-memory
  `request_id → callback` map, then publishes the first message of the use case.
- A "callback" is deliberately abstract — it may be a long-polling HTTP response held open, an
  HTTP callback URL the caller supplied, a WebSocket send, a Slack `response_url` POST, etc. New
  callback kinds can be added without changing the gateway's core registry/timeout/shutdown
  logic.
- When a message arrives on the gateway's inbound subscription carrying a known `request_id`, the
  gateway looks up the registry entry, invokes the callback, and removes the entry.
- **Current implementation note**: the registry is in-memory only (single gateway instance
  assumption). Distributed/persistent callback handling is intentionally deferred — see
  `backlog.md`.

### Per-use-case timeout

Every use case must declare a timeout. The gateway starts a timer when it registers a
`request_id`; if no terminal message arrives before the timeout elapses:

1. The gateway synthesizes a failure response and delivers it through the same callback mechanism
   as a real response.
2. The gateway removes the `request_id` entry from its registry.

The timeout is the catch-all: it covers any request that never produces a terminal message,
including a function that hangs or crashes mid-chain. A function that can *detect* its own expected
terminal failure may additionally fail fast via the error short-circuit below.

### Error short-circuit (terminal error stage)

A function that detects an expected, terminal failure (e.g. invalid user input, or an upstream data
source returning no usable result) may publish a terminal **error stage** (conventionally `FAILED`)
on its own topic instead of continuing the chain. The message carries the originating `request_id`
and a human-readable reason. The gateway owns a subscription filtered to that error stage; on
receipt it fails the caller through the same failure-response + registry-cleanup path used for
timeouts, *before* the per-use-case timeout would elapse — so the caller fails faster and gets a
specific message ("no match for that city in that state") instead of a generic timeout.

The convention is **optional and per-stage**: a use case adopts it only for failures a function can
classify as terminal. Failures it cannot detect (a hung/crashed function, or an LLM stage that
silently produces nothing) still fall back to the timeout. Extending it to another function later
needs only a new filtered gateway subscription — no change to the gateway's core
registry/timeout/shutdown logic. Rollout state is tracked in `backlog.md`.

### Graceful shutdown

Configured via `application.yaml`, three stages:

1. **Drain**: the gateway stops accepting new requests but keeps running, waiting up to a
   configurable duration for in-flight `request_id`s to receive their real response.
2. **Force-fail in-flight**: for every `request_id` still pending after the drain window, the
   gateway immediately fires that request's failure response (same synthesized-failure path as a
   timeout) and cleans up the registry entry.
3. **Forced shutdown**: the process exits (or the platform sends `SIGKILL` after its own grace
   period, e.g. Cloud Run's SIGTERM→SIGKILL window).

A newly started gateway instance has an empty registry. If it receives a response message for a
`request_id` it never registered (e.g. because the previous instance shut down and this is a
stale/duplicate redelivery), it logs a warning and discards the message. This is a deliberate,
accepted gap under the current in-memory/single-instance design — see `backlog.md`.

## Functions

- Each function is triggered by messages on the Pub/Sub subscription(s) it owns, and may publish
  to zero, one, or multiple downstream topics (fan-out to multiple next stages is allowed).
- **Idempotency is required**: given the same `request_id` + `stage`, reprocessing a message must
  produce the same outcome (no duplicate downstream publishes, no duplicated external side
  effects). This is necessary because Pub/Sub delivery is at-least-once.
  - **Exception: claude-automator.** Any function whose side effect is an LLM call cannot
    guarantee an identical output on reprocessing — the LLM will not return byte-identical
    results, only interchangeable ones. Such functions are exempt from output-identity, but must
    still be *safe to retry*: reprocessing must not duplicate downstream publishes or otherwise
    double-apply side effects that aren't idempotent by nature.

### Runtime stack (stateless functions)

Stateless `xxxsvc` functions use a **plain Java Cloud Run function on the GCP Functions Framework** —
**not Spring Boot**. Spring Boot is reserved for the always-on, stateful gateway (`gateway-svc`); a
short-lived function gains nothing from a Spring context and only pays its cold-start and wiring cost.
The stack (single Maven module; `functions-framework-api`, `libraries-bom` + `google-cloud-pubsub`,
gson, JDK `java.net.http.HttpClient`) is modeled on the knowledge-base POC
`knowledgebase/frameworks/gcp/function/cloud-run-java-pubsub-relay-poc.md`.

Prefer a plain **`HttpFunction` behind a Pub/Sub push subscription** over an Eventarc trigger, so the
consumer-owned-subscription + immutable-filter + per-subscription-DLQ conventions in
[`arch/topics-and-provisioning.md`](arch/topics-and-provisioning.md) hold (an Eventarc trigger manages
its own hidden subscription); parse the push envelope manually. First adopter: `weather-svc` (see
`weather-svc/docs/architecture.md`). Revisit only for a stateful/always-on service like the gateway.

### Side effects in functions

Side effects are expected and encouraged inside a function. Beyond calling external APIs and
writing to databases, other side effects a function may perform:

- Publish to multiple downstream topics (fan-out).
- Call any **other GCP service** for information or storage (Firestore, Cloud Storage, Secret
  Manager, etc.) — modeled as a single dependency block in diagrams, since gateway and every
  function may reach it.
- Enqueue a delayed/scheduled continuation via **Cloud Tasks** for a business-logic-driven delay
  (e.g. "poll this external async job again in 30s") — distinct from Pub/Sub's transient-failure
  redelivery. A function creates a one-time Cloud Task with a future `scheduleTime` targeting its
  own HTTP entrypoint. Not yet used by any use case; the full pattern and its trigger condition
  live in `backlog.md`.

## Conventions (detail docs)

The message contract and the Pub/Sub wiring conventions live in their own docs under `arch/` so
this page stays focused on the system model. Read them when building or provisioning a stage:

- [`arch/messaging.md`](arch/messaging.md) — the shared message envelope, Protobuf/Pub-Sub schema
  enforcement (validation happens at publish time), and schema-evolution rules.
- [`arch/topics-and-provisioning.md`](arch/topics-and-provisioning.md) — topic/subscription/DLQ
  ownership and naming, and the per-service `provision-pubsub.sh` convention (each service
  provisions only what it owns).

## Accepted risks / not yet solved

- **A/B/C never share a database.** Stages in a use case are assumed not to have cross-stage data
  races since they don't share storage; this is accepted as a risk rather than actively verified.
  Tracked in `backlog.md`.
- **In-memory callback registry.** The gateway's `request_id → callback` map is not persisted or
  distributed. A gateway restart loses in-flight registrations (mitigated somewhat by the
  graceful-shutdown drain/force-fail sequence above, but a hard crash still loses state).
  Distributed/persistent handling is deferred — see `backlog.md`.

See `backlog.md` for the full roadmap and known gaps against this design.
