---
status: DRAFT
---

# Messaging mechanism

## Overview: Pub/Sub as the transport

`claude-automator` talks outward only through GCP Pub/Sub — **two** inbound subscriptions (QA on
`gateway-requests`, WEATHER on `weather-svc-results`) and **one** outbound topic
(`claude-automator-responses`, shared by both use cases and distinguished by the `use_case`/`stage`
attributes; see the repo-root
[`docs/arch/topics-and-provisioning.md`](../../../docs/arch/topics-and-provisioning.md) for topic
ownership). Pub/Sub decouples it from the other end and lets it process asynchronously, on its own
schedule, with no connection held open.

## Input mechanism: sync pull, not push

A Pub/Sub subscription can deliver by **push** (Pub/Sub calls an HTTP endpoint the consumer
exposes) or **pull** (the consumer calls the API itself). Push is ruled out for this module, not
just unexplored: it requires a stable, publicly reachable HTTPS endpoint registered on the
subscription ahead of time, and claude-automator has no such endpoint to offer — it runs without
public ingress (see the repo-root [`docs/backlog.md`](../../../docs/backlog.md): "the gateway is being
run locally for now rather than deployed to Cloud Run"). Standing one up would mean adding a new
always-on component just to host it, which is a different architecture, not a tweak to this one.

`claude-automator` uses **synchronous pull**: the `SessionStart` hook (`poll-useful-message.ts`)
calls `subClient.pull({ subscription, maxMessages: 1 })`, up to `POLL_COUNT` times, sleeping
`POLL_INTERVAL_MS` between empty results. With two inbound subscriptions it tries each per round (QA
first, then WEATHER); the first non-empty pull wins and the session works that one message — keeping
the single-session-at-a-time shape. If nothing arrives on either, the session starts anyway with a
"nothing to do" context instead of a task. See "Future optimization" below for the tradeoff this
choice makes and when it'd be worth revisiting.

## Output mechanism: direct publish

The `Stop` hook (`end-session.ts`) makes one `publish()` call to the outbound topic once Claude
Code finishes answering, and separately acknowledges the original inbound message against the
subscription it arrived on (see [`disk-correlation.md`](disk-correlation.md) for how it knows which
message, and on which subscription). The published envelope echoes the inbound `use_case` on both
body and attributes (`QA/ANSWERED` or `WEATHER/ANSWERED`) rather than being hardcoded to one use
case. No polling on this side — `claude-automator`'s job ends at that single publish call.

## Future optimization: StreamingPull

A second pull mode, unused today: **StreamingPull** — the subscriber opens a persistent stream and
Pub/Sub delivers messages over it as they arrive, removing the latency floor synchronous pull has
today (see [`../use-cases/nothing-to-do.md`](../use-cases/nothing-to-do.md)'s "Timing" section).
Not used because no current use case has a latency or throughput requirement that justifies
trading the simplicity of a single stateless `pull()` call for stream lifecycle and lease-extension
handling. Revisit if that changes.
