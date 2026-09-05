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
- **claude-automator's failure paths aren't diagrammed yet.** `claude-automator-dev/docs/use-cases/`
  currently has `happy-path.md` and `nothing-to-do.md` — the success case and the empty-poll case.
  At least three other paths exist in the code but aren't written up anywhere as sequence
  diagrams; do so following the same template:
  - **Ack failure after a successful publish is mislabeled and risks a duplicate answer.**
    `sendMessage()` (`pubsub-client.ts`) publishes the answer, then acks the original inbound
    message. If `subClient.acknowledge()` itself throws, that exception is caught by
    `end-session.ts`'s generic `catch`, which records `reason=publish_error` even though the
    answer was already published. Because the ack never happened, the input message is still
    unacked and will redeliver, so a later session can re-answer the same question — a real
    duplicate-answer risk, not just a mislabeled metric.
  - **A silent disk-write failure in `SessionStart` discards a pulled message's answer.**
    `writeContent()` (`filesystem.ts`) catches its own errors and returns `false`, but
    `processUsefulMessage()` (`pubsub-client.ts`) never checks that return value when writing
    `UUID_PATH`/`ACK_ID_PATH`. If the write fails (disk full, permissions), the pulled message's
    `metadata` is still handed to Claude Code as if nothing went wrong. `Stop` later can't find
    the UUID file, aborts before publishing (`reason=uuid_missing`), and the original input
    message — never acked — just redelivers on a later session. The answer that session produced
    is silently thrown away.
  - **Invalid-envelope nack → redeliver → DLQ.** `deserialize()` (`pubsub-client.ts`) returns
    `null` on a malformed body or a `use_case`/`stage` mismatch, and `pollPubSub()` nacks via
    `modifyAckDeadline(0)`. The mechanism is commented in code but the end-to-end path (nack,
    redeliver, repeat across polls/sessions, eventual DLQ after max delivery attempts) has never
    been diagrammed.

  Suggested treatment: give the three paths above their own sequence diagrams — each spans
  multiple processes/sessions and leaves disk/Pub/Sub state that the existing docs don't show. Two
  more paths exist but are thinner and probably don't warrant their own diagram: a transient
  `pull()` failure (caught in `poll-useful-message.ts`, distinct `reason=poll_error` and
  `additionalContext` text from the empty-poll case, but otherwise the same shape as
  `nothing-to-do.md`) — fold this in as a note on that doc rather than a new file — and the
  PID-file-missing-on-kill / OTLP-export-failure / benign-empty-`metadata`-ack-drop cases, which
  are already deliberately swallowed or covered by `metrics.md` and don't need new diagrams.
- **No automated tests exist for `claude-automator`.** No test runner is configured (no `test`
  script in `package.json`). `pubsub-client.ts` and `filesystem.ts` are the highest-value starting
  points, given the failure-path gaps documented above.
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
- **Gateway-initiated (unsolicited) delivery is designed but not implemented.** `architecture.md`
  documents this as a gateway capability, needed by `use-cases/article-summary.md`, which has no
  caller connection to deliver through. Today's `AnswerSink`/`PendingAnswerRegistry` machinery
  requires a `register(requestId, sink)` call before `complete(requestId, message)` can find
  anything to invoke — there's no code path for the gateway to act on an inbound message with no
  prior registration. The destination-config *shape* is decided (see `architecture.md`,
  "Gateway-initiated (unsolicited) delivery," and `gateway-svc/docs/draft-notes.md`), but nothing
  implements it — no such config exists and no code loads it. Needs: a direct-delivery mechanism
  keyed on `use_case`+`stage` (not `request_id`) that bypasses the registry entirely, the config
  entries plus their loading/binding code, and (for Slack) a bot-token `chat.postMessage`-style send
  distinct from the existing `response_url`-based `ctx.respond` path used by `/ask`. Two further
  requirements added by the edge-case review, both easy to miss when implementing:
  - **Delivery-mode dispatch must precede the registry lookup.** `GatewayResponseSubscriber` must
    route on `use_case`+`stage` *first* and only fall through to `PendingAnswerRegistry` for
    registered-callback use cases. Implemented the other way round, every unsolicited message hits
    the "unknown `request_id`" branch and is logged-and-discarded — a silent total failure of the
    feature that would still pass a naive smoke test if the discard log went unread.
  - **Duplicate suppression by `request_id` on this path.** The registered-callback path dedups
    implicitly (the registry entry is consumed on first completion); a stateless direct-delivery
    handler does not, so at-least-once redelivery posts the same Slack message twice. Needs a
    remembered-`request_id` set covering the redelivery window. Shares the durability question of
    the in-memory callback registry below — a restart clears it either way.
- **No alerting on DLQ depth, which is the only failure signal for use cases with no caller.** For
  a caller-originated use case, a failure eventually surfaces to a human via the caller's timeout.
  For a scheduled-task-originated one (see `architecture.md`) there is no caller and no timeout
  delivery, so a nacked-then-dead-lettered message — including a failed *terminal* delivery, where
  all the work succeeded and only the final external send failed — is entirely silent. Nothing
  currently watches DLQ depth on any subscription. Folds into the observability item above, but is
  called out separately because it is the specific signal `use-cases/article-summary.md` depends on
  and the only one it has.
- **Message-size limits are undefended across the board.** No service currently bounds or truncates
  outbound `payload`/`metadata`, which was harmless while every message was a short question or
  answer. `ARTICLE_SUMMARY` introduces content fetched from arbitrary third-party RSS feeds, where
  an oversized message is a realistic publish-time failure (see `architecture.md`, "Blast radius of
  sharing one topic and one instance"). Decide per originating service whether to truncate, split,
  or offload large content (e.g. to Cloud Storage with a reference in the envelope) — the last of
  these would be a system-level envelope change and should come back here if chosen.

## Mismatches: current code vs. standards in `architecture.md`

- ~~**Topic naming is backwards on the request side.**~~ **Fixed.** `gateway-svc`'s config keys and
  env vars have been renamed to reflect actual ownership: the gateway's outbound topic is
  `gateway-requests` (config prefix `gcp.pubsub.gateway-requests`, env var
  `GATEWAY_REQUESTS_PUBSUB_TOPIC_ID`), and claude-automator's outbound topic is
  `claude-automator-responses` (config prefix `gcp.pubsub.claude-automator-responses`, env var
  `CLAUDE_AUTOMATOR_RESPONSES_PUBSUB_SUBSCRIPTION_ID` on the gateway side, `PUBSUB_TOPIC_ID` /
  `PUBSUB_SUBSCRIPTION_ID` on the claude-automator side). Provisioned by
  `gateway-svc/scripts/provision-pubsub.sh` and `claude-automator-dev/claude-automator/scripts/provision-pubsub.sh`
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
- **claude-automator is QA-only in code; `architecture.md` now specifies multi-use-case dispatch.**
  Verified in code: `config.ts` exposes a single `PUBSUB_TOPIC_ID`/`PUBSUB_SUBSCRIPTION_ID` pair,
  and `pubsub-client.ts` hardcodes `QA_USE_CASE = "QA"` plus its `ASKED`/`ANSWERED` stage constants
  as module-level literals — both the inbound envelope validator and the outbound publish are
  locked to this one use case. No longer an accepted shape: `architecture.md`
  ("claude-automator: multi-use-case dispatch") now specifies one running instance owning multiple
  subscriptions (one per registered use case) and dispatching per message via a per-use-case
  registry entry (subscription → prompt template → outbound stage), with `QA` as that registry's
  first entry, unchanged in behavior. Reopens claude-automator's own service-level design (currently
  `ACCEPTED`, QA-only) — see `PIPELINE.md`. Still open, deferred to that service-level pass:
  - The concrete registry format (static config file vs. code-defined table) and where it lives.
  - How N concurrent poll loops share the process (one async loop per subscription vs. some other
    concurrency structure) without regressing the existing single-loop `POLL_COUNT`/
    `POLL_INTERVAL_MS` behavior QA relies on today.
  - Migration path for existing QA deployments/config (env vars, `.env`, deploy scripts) onto
    whatever the new registry format turns out to be, without a breaking redeploy.
- **`scripts/provision-pubsub-schema.sh`'s topic list is manually maintained and will be stale for
  `article-summary`.** Its `TOPICS_USING_SCHEMA` array currently lists only `gateway-requests` and
  `claude-automator-responses`. `arch/messaging.md` requires the envelope schema on *every* topic
  carrying it, but nothing enforces the list — a new topic omitted from it is created fine and
  carries unvalidated traffic with no error anywhere. `article-svc-requests` must be added when
  `article-svc` is implemented (see `use-cases/article-summary.md`). Longer term, deriving the list
  from the per-service scripts, or failing the script when it finds a topic without an attached
  schema, would close the gap properly.
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
  `gateway-svc/scripts/provision-pubsub.sh` and `claude-automator-dev/claude-automator/scripts/provision-pubsub.sh`
  cover topic/subscription/DLQ creation for the Q&A flow, each provisioning only what it owns
  (plus the repo-root `scripts/provision-pubsub-schema.sh` for the shared envelope schema). A
  `deploy.sh` for the gateway itself was deliberately not written: the gateway is being run
  locally for now rather than deployed to Cloud Run — write this script when that changes.
  Per-function (`xxxsvc`) deploy scripts also remain unstarted since no such function exists in
  the tree yet (`claude-automator` is not a Cloud Run Function and is deployed differently).
- **No CI pipeline builds/publishes the `claude-automator` image.** Today the image is built
  locally from a checkout via `docker build -t claude-automator claude-automator-dev/claude-automator`
  (see `claude-automator-dev/docs/deploy/README.md`) — there's no automation that builds the image
  on tag/release, pushes it to a registry, or produces a versioned tag. Add a GitHub Actions
  workflow that checks out the repo, builds the image with that same context, and tags/releases it
  (e.g. on a version tag push), and publishes it somewhere pullable, once `claude-automator` is
  treated as a deployable package rather than a build-it-yourself artifact.

## Sub-agent workflow (dev tooling)

- **The architecture-first sub-agent pipeline's phase gates are convention-only, not enforced.**
  The gated pipeline (`system-architect` → `service-architect` → `implementer`, see the "Sub-agent
  workflow" section of the repo-root `AGENTS.md`) relies on rules stated in `AGENTS.md` — don't
  skip a phase, don't write outside a service's docs scope, don't implement against a
  non-`ACCEPTED` design, stay off `main`. But `AGENTS.md` is context Claude reads, not enforced
  configuration: there is no compliance guarantee, so any of these gates *can* be skipped,
  especially on a vague or ambiguous request. **Rough next step:** add a `PreToolUse` hook as a
  hard enforcement layer — e.g. block the `implementer` sub-agent's `Write`/`Edit` unless the
  target service's `architecture.md` is `status: ACCEPTED`, and/or path-scoped `permissions`
  rules confining each architect's `Write`/`Edit` to its own docs root (`docs/**` for
  `system-architect`, `<service>/docs/**` for `service-architect`). Revisit if the convention-only
  version misfires in practice.
