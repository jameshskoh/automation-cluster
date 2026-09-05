# Draft notes (pre-service-architect)

See the `draft-notes.md` convention in the root `AGENTS.md`. Detail here was moved out of the
system-level `docs/architecture.md` during the article-summary workflow design (2026-09-05), for
`@service-architect gateway-svc` (or whoever formalizes gateway-svc's own design under this docs
root — see `docs/PIPELINE.md` footnote ¹) to pick up when it takes on the gateway-initiated
(unsolicited) delivery obligation.

## Gateway-initiated (unsolicited) delivery — code-shape notes

The system-level requirement (see `../../docs/architecture.md` "Gateway-initiated (unsolicited)
delivery"): a delivery path that acts on a terminal message with no prior registration, reading its
destination from configuration rather than the request.

Concrete code shape as originally sketched, for the service-level design to accept/revise:

- Today's registered-callback path is `PendingAnswerRegistry.register(requestId, sink)` /
  `.complete(requestId, message)` — an `AnswerSink` must be registered before it can be invoked.
- The new path should **not** be modeled as an `AnswerSink` variant that skips registration — that
  would carry TTL/`onExpire` eviction semantics that don't apply and would mislead readers of the
  code. Model it as its own, simpler concept: a direct-delivery handler keyed on `use_case`+`stage`
  (not `request_id`).
- The existing Slack callback send goes through `ctx.respond`/a `response_url` obtained from the
  inbound slash command. The new path needs a different code path: a standing bot-token-authenticated
  send (e.g. Slack's `chat.postMessage`) to a destination read from configuration, set up ahead of
  time per use case.
- Destination config as originally sketched: a new block in `application.yaml`
  (`gateway-svc/gateway-bootstrap/src/main/resources/application.yaml`), following the existing
  env-var-driven pattern used for Pub/Sub topic/subscription ids, shaped as `use_case`+`stage` →
  destination adapter + adapter-specific target (e.g. adapter `slack` plus a channel id) — generic
  across adapters, not a Slack-only shape. Bound and read once at startup, not re-fetched per
  delivery.
