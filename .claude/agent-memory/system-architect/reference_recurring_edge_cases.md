---
name: edge-cases-non-gateway-originated-flows
description: The recurring classes of system-level edge case that non-gateway-originated flows expose - check these first on any new async flow
metadata:
  type: reference
---

Categories of system-level gap that surfaced when reviewing the first non-gateway-originated flow.
Worth re-checking on every future use case, since each one recurs whenever a flow lacks a held-open
caller connection or shares infrastructure with an existing use case.

- **Schema attachment is repo-root-owned, not per-service.** `scripts/provision-pubsub-schema.sh`
  holds a hardcoded `TOPICS_USING_SCHEMA` list. Every *new topic* must be added there and the
  script re-run after the topic exists — no per-service `provision-pubsub.sh` covers it, so it is
  easy to miss when the use-case doc says "each service provisions what it owns."
- **Subscription naming collides on a second subscription by the same consumer against the same
  topic.** The convention `<consuming-service>-<topic-name>-sub` is a function of consumer+topic
  only, so a consumer needing two filtered subscriptions on one topic has no unique name.
- **Nobody observes failure without a caller.** A held-open connection makes failure visible via
  timeout. Without one, a nack → retry → DLQ on the *terminal* leg is silent. Distinct from a
  *stuck* chain, which the timeout at least logs.
- **Redelivery dedup has no owner on an unsolicited-delivery leg.** The registered-callback path
  dedups naturally (registry entry removed on first complete). A stateless `use_case`+`stage`
  delivery path does not, and the originating service cannot fix it — it is downstream of them.
- **Shared topic/instance blast radius.** Reusing one publisher topic and one `claude-automator`
  instance across use cases couples their schema evolution and their message-size/resource limits.

**How to apply:** run this list against any new use-case draft before signing off, especially the
first flow of a new *shape* (no caller, fan-out, multi-consumer).
