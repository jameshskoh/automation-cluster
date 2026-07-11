# Roadmap / known gaps

Items intentionally deferred from the current architecture (see `architecture.md`), plus
mismatches between the current codebase and the standards described there.

## Deferred design work

- **Observability**: structured logging, distributed tracing, and metrics across the gateway and
  all functions. No convention exists yet for correlating logs/traces by `request_id` across
  service boundaries. `claude-automator` already exports OTLP metrics
  (`OTLP_METRICS_URL`) — figure out whether to extend that pattern repo-wide or standardize on
  something else.
- **claude-automator's "nothing to do" case reports as a metric failure.** When `SessionStart`'s
  poll finds no message, the `Stop` hook still runs, finds no `UUID_PATH`, and records
  `claude_hook_completed_total{hook_event=Stop, outcome=failure, reason=uuid_missing}` — but this
  is expected, benign behavior, not a real failure. It conflates with genuine publish/ack errors
  in failure-rate metrics. Needs its own outcome (or to be skipped entirely) instead of reusing
  `failure`.
- **Distributed/persistent callback registry**: the gateway's `request_id → callback` map is
  in-memory only (see `architecture.md`). A hard crash (not a graceful shutdown) loses all
  in-flight registrations. Needs a decision on whether/how to back this with persistent storage
  (Firestore, etc.) if multi-instance gateways or crash-resilience become requirements.
- **Cross-stage data race risk**: stages A/B/C in a use case are assumed not to share a database
  and therefore not to race each other. This is an accepted assumption, not a verified guarantee
  — revisit if a use case ever needs shared state between stages.
- **Schema evolution enforcement**: the rule (no field removal, new fields must be optional with
  defaults) is currently a convention only. No CI or code-review checklist item enforces that a
  new Protobuf schema revision doesn't remove/renumber fields.
- **Fast-fail / error short-circuit**: today, a mid-chain function failure is only detected by the
  gateway's per-use-case timeout — a deliberate, fast, "expected" failure (e.g. invalid user
  input) looks identical to a hung request until the timeout elapses. A lightweight optional
  `error` stage convention (a function publishes directly to this stage on an expected failure,
  short-circuiting to the gateway) could let the gateway fail fast instead of waiting out the
  timeout. Not designed in detail; revisit if fast-fail UX becomes a requirement.
- **Cloud Tasks delayed/scheduled continuation**: documented in `architecture.md` as a pattern for
  when a function needs to "come back later" outside of Pub/Sub's own retry policy (e.g. polling
  an external async job). No current use case needs this yet — implement when one does.

## Mismatches: current code vs. standards in `architecture.md`

- ~~**Topic naming is backwards on the request side.**~~ **Fixed.** `gateway-svc`'s config keys and
  env vars have been renamed to reflect actual ownership: the gateway's outbound topic is
  `gateway-requests` (config prefix `gcp.pubsub.gateway-requests`, env var
  `GATEWAY_REQUESTS_PUBSUB_TOPIC_ID`), and claude-automator's outbound topic is
  `claude-automator-responses` (config prefix `gcp.pubsub.claude-automator-responses`, env var
  `CLAUDE_AUTOMATOR_RESPONSES_PUBSUB_SUBSCRIPTION_ID` on the gateway side, `PUBSUB_TOPIC_ID` /
  `PUBSUB_SUBSCRIPTION_ID` on the claude-automator side). Provisioned by
  `gateway-svc/scripts/provision-pubsub.sh` and `claude-automator/scripts/provision-pubsub.sh`
  (each service provisions only what it owns — see `arch/topics-and-provisioning.md`).
  The Java property/class names at the time (`AiRequestPubSubProperties`,
  `AiResponsePubSubProperties`, `AiRequest`/`AiResponse` domain records) were intentionally left
  as-is for this change — only the config prefixes and topic/subscription names changed. (They
  were renamed in a later pass — see the module-rename item below.)
- ~~**`ai-svc` is the gateway but isn't named/organized as one yet.**~~ **Fixed.** The module has
  been renamed `gateway-svc` (directory, Maven artifact ids, packages —
  `com.jameshskoh.gateway`), making clear this module *is* the gateway rather than a
  Q&A-specific `ai-svc`. `AiRequest`/`AiResponse` were replaced by a shared `GatewayMessage`
  domain record as part of the same change (see the unified-envelope item below).
- ~~**No unified message envelope yet.**~~ **Fixed for the Q&A flow.** The Q&A use case now uses
  the shared `GatewayMessage` envelope (`use_case`, `stage`, `request_id`, `payload`, `metadata`)
  on both legs. For this use case specifically, the question and answer are each conversational
  text and so are carried in `metadata`; `payload` is sent empty (`""`) — a future use case may
  use `payload` for non-conversational data. Both publishers set `use_case`/`stage` as Pub/Sub
  message attributes (not just body fields), so the attribute-based subscription filters set up by
  `provision-pubsub.sh` now match real traffic. claude-automator also validates the inbound
  envelope body (shape + `use_case`/`stage`) before acting, no longer relying on the Pub/Sub filter
  as its sole data-contract check — see the claude-automator item below.
- ~~**No Pub/Sub schema configured on any topic.**~~ **Fixed.** `schemas/gateway_message.proto`
  defines the envelope as a Protobuf schema; `scripts/provision-pubsub-schema.sh` registers it via
  `gcloud pubsub schemas create` and attaches it (with `--message-encoding=json`, since messages
  are still sent as plain JSON) to both `gateway-requests` and `claude-automator-responses`.
- ~~**No DLQ configured on any subscription.**~~ **Fixed for the Q&A flow.** Each service's
  `provision-pubsub.sh` creates a dead-letter topic for the subscription(s) it owns and wires
  `--dead-letter-topic` + IAM bindings for the Pub/Sub service agent. `GatewayResponseSubscriber`
  (formerly `AiResponseSubscriber`) now nacks on failure, so poison messages redeliver and, after
  the subscription's max delivery attempts, reach the DLQ instead of being silently acked-and-dropped.
- **claude-automator correlates requests via files on disk.** It reads/writes the shared
  `GatewayMessage` envelope shape (`use_case`, `stage`, `request_id`, `payload`, `metadata`),
  correctly populates `use_case`/`stage` on its outbound `ANSWERED` message, and now validates the
  inbound envelope body against a zod schema (shape + `use_case="QA"`/`stage="ASKED"`) before acting
  — nacking a message that fails validation so it redelivers and eventually dead-letters, rather
  than relying on the Pub/Sub subscription filter as its sole data-contract check. Remaining gap:
  it still correlates requests via files on disk (`UUID_PATH`, `ACK_ID_PATH`, populated from the
  envelope's `request_id`) rather than an in-process mechanism. That remains unstarted.
- **No per-use-case timeout configuration.** `gateway-svc` has a `qa.async.timeout-millis` (HTTP
  long-poll timeout) and a separate `qa.pending.ttl-millis` (registry eviction sweep), which
  together approximate the gateway's timeout-then-cleanup design — but this is hardcoded to the
  one Q&A use case rather than being a declared, per-use-case property as `architecture.md`
  describes. Generalizing this to a per-use-case timeout config (so a new use case can declare its
  own timeout without touching shared config keys) is unstarted.
- **No graceful-shutdown drain/force-fail/forced-shutdown sequence implemented.** Nothing in
  `gateway-bootstrap` currently drains in-flight registry entries or force-fails them on shutdown;
  only the TTL sweeper exists, which is a different mechanism (periodic eviction, not a shutdown
  hook).
- **Pub/Sub provisioning exists (per-service); gateway deploy script does not.**
  `gateway-svc/scripts/provision-pubsub.sh` and `claude-automator/scripts/provision-pubsub.sh`
  cover topic/subscription/DLQ creation for the Q&A flow, each provisioning only what it owns
  (plus the repo-root `scripts/provision-pubsub-schema.sh` for the shared envelope schema). A
  `deploy.sh` for the gateway itself was deliberately not written: the gateway is being run
  locally for now rather than deployed to Cloud Run — write this script when that changes.
  Per-function (`xxxsvc`) deploy scripts also remain unstarted since no such function exists in
  the tree yet (`claude-automator` is not a Cloud Run Function and is deployed differently).
