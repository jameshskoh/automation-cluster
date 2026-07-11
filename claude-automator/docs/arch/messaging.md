# Messaging mechanism

## Overview: Pub/Sub as the transport

`claude-automator` talks outward only through GCP Pub/Sub — one topic/subscription pair inbound,
one outbound (see the repo-root
[`docs/arch/topics-and-provisioning.md`](../../../docs/arch/topics-and-provisioning.md) for topic
ownership). Pub/Sub decouples it from whatever's on the other end and lets it process a request
asynchronously, on its own schedule, with no connection held open.

## Input mechanism: pull, not push

A Pub/Sub subscription can deliver by **push** (Pub/Sub calls an HTTP endpoint the consumer
exposes) or **pull** (the consumer calls `pull` itself). `claude-automator` uses pull: the
`SessionStart` hook (`poll-useful-message.ts`) calls
`subClient.pull({ subscription, maxMessages: 1 })`, up to `POLL_COUNT` times, sleeping
`POLL_INTERVAL_MS` between empty results. If nothing arrives, the session starts anyway with a
"nothing to do" context instead of a question.

Why pull over push hasn't been explored in depth — noted here for later, not a settled tradeoff.

## Output mechanism: direct publish

The `Stop` hook (`end-session.ts`) makes one `publish()` call to the outbound topic once Claude
Code finishes answering, and separately acknowledges the original inbound message (see
[`disk-correlation.md`](disk-correlation.md) for how it knows which message that is). No polling
on this side — `claude-automator`'s job ends at that single publish call.
