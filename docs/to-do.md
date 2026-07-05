# Roadmap / known gaps

Items intentionally deferred from the current architecture (see `architecture.md`), plus
mismatches between the current codebase and the standards described there.

## Deferred design work

- **Observability**: structured logging, distributed tracing, and metrics across the gateway and
  all functions. No convention exists yet for correlating logs/traces by `request_id` across
  service boundaries. `claude-automator` already exports OTLP metrics
  (`OTLP_METRICS_URL`) — figure out whether to extend that pattern repo-wide or standardize on
  something else.
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

- ~~**Topic naming is backwards on the request side.**~~ **Fixed.** `ai-svc`'s config keys and env
  vars have been renamed to reflect actual ownership: the gateway's outbound topic is
  `gateway-requests` (config prefix `gcp.pubsub.gateway-requests`, env var
  `GATEWAY_REQUESTS_PUBSUB_TOPIC_ID`), and claude-automator's outbound topic is
  `claude-automator-responses` (config prefix `gcp.pubsub.claude-automator-responses`, env var
  `CLAUDE_AUTOMATOR_RESPONSES_PUBSUB_SUBSCRIPTION_ID` on the gateway side, `PUBSUB_TOPIC_ID` /
  `PUBSUB_SUBSCRIPTION_ID` on the claude-automator side). Provisioned by
  `ai-svc/scripts/provision-pubsub.sh` and `claude-automator/scripts/provision-pubsub.sh` (each
  service provisions only what it owns — see `arch/topics-and-provisioning.md`).
  The Java property/class names (`AiRequestPubSubProperties`,
  `AiResponsePubSubProperties`, `AiRequest`/`AiResponse` domain records) were intentionally left
  as-is — only the config prefixes and topic/subscription names changed.
- **`ai-svc` is the gateway but isn't named/organized as one yet.** The architecture calls the
  always-on stateful service "the gateway"; the module is currently `ai-svc` with an
  `AiRequest`/`AiResponse`-shaped domain that's specific to one use case (Q&A), not a generic
  gateway. Decide on and execute a rename (directory, Maven artifact ids, packages) to make clear
  this module *is* the gateway, separate from the topic renames already done above.
- **No unified message envelope yet.** `AiRequest` (`uuid`, `question`) and `AiResponse` (`uuid`,
  `answer`) are one-off Java records serialized as plain JSON via Jackson. They have no
  `use_case`, `stage`, `payload`/`metadata` split, and are not backed by a Protobuf/Pub-Sub
  schema. Every function currently would need to invent its own message shape rather than sharing
  the unified envelope described in `arch/messaging.md`. (Both `provision-pubsub.sh` scripts set up
  attribute-based filtering as if `use_case=QA`/`stage=ASKED|ANSWERED` were already being
  published — until the envelope + attributes are actually added to the publishers, the filters
  will match nothing.)
- **No Pub/Sub schema configured on any topic.** Neither `provision-pubsub.sh` script attaches a
  schema; messages are still unvalidated JSON blobs until the Protobuf envelope exists (see above)
  and gets attached via `gcloud pubsub schemas create` + `--schema-name` on topic creation.
- ~~**No DLQ configured on any subscription.**~~ **Fixed for the Q&A flow.** Each service's
  `provision-pubsub.sh` creates a dead-letter topic for the subscription(s) it owns and wires
  `--dead-letter-topic` + IAM bindings for the Pub/Sub service agent. `AiResponseSubscriber` still
  acks-and-drops on failure instead of nacking (see its inline `TODO`) — now that a DLQ exists,
  that should be changed to nack so poison messages actually reach the DLQ instead of being
  silently dropped.
- **claude-automator has no use_case/stage concept.** It reads `PUBSUB_TOPIC_ID` /
  `PUBSUB_SUBSCRIPTION_ID` as flat, single-purpose env vars (now pointed at the correctly-owned
  `claude-automator-responses` topic / `claude-automator-gateway-requests-sub` subscription) and
  correlates requests via files on disk (`UUID_PATH`, `ACK_ID_PATH`) rather than via the unified
  message envelope's `request_id` field. Bringing it in line with the unified format (and
  publishing `use_case`/`stage` attributes so the subscription filters in `provision-pubsub.sh`
  actually match something) is unstarted.
- **No per-use-case timeout configuration.** `ai-svc` has a `qa.async.timeout-millis` (HTTP
  long-poll timeout) and a separate `qa.pending.ttl-millis` (registry eviction sweep), which
  together approximate the gateway's timeout-then-cleanup design — but this is hardcoded to the
  one Q&A use case rather than being a declared, per-use-case property as `architecture.md`
  describes. Generalizing this to a per-use-case timeout config (so a new use case can declare its
  own timeout without touching shared config keys) is unstarted.
- **No graceful-shutdown drain/force-fail/forced-shutdown sequence implemented.** Nothing in
  `ai-bootstrap` currently drains in-flight registry entries or force-fails them on shutdown; only
  the TTL sweeper exists, which is a different mechanism (periodic eviction, not a shutdown hook).
- **Pub/Sub provisioning exists (per-service); gateway deploy script does not.**
  `ai-svc/scripts/provision-pubsub.sh` and `claude-automator/scripts/provision-pubsub.sh` cover
  topic/subscription/DLQ creation for the Q&A flow, each provisioning only what it owns. A
  `deploy.sh` for the gateway itself was deliberately not written: the gateway is being run
  locally for now rather than deployed to Cloud Run — write this script when that changes.
  Per-function (`xxxsvc`) deploy scripts also remain unstarted since no such function exists in
  the tree yet (`claude-automator` is not a Cloud Run Function and is deployed differently).
