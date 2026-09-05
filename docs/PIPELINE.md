# Pipeline status

Read first, every session — the durable, cross-session record of what's done and what's unblocked.
Updated by `system-architect`, `service-architect`, and `implementer` on `status: ACCEPTED` (see the
`AGENTS.md` "Sub-agent workflow" section). Safe to hand-edit for a new service row or a stale entry.

This is the per-phase/per-service summary; per-artifact detail lives in each doc's `status:`
frontmatter. `docs/backlog.md` is separate — deferred work and doc-vs-code divergence, not progress.

## System design

- status: ACCEPTED (pre-existing — `docs/use-cases/qa.md`)
- status: ACCEPTED (2026-09-05) — the article-summary workflow, including the second review pass
  that hunted specifically for system-level edge cases. See `docs/use-cases/article-summary.md` for
  the use case itself. Accepted together: `docs/architecture.md`,
  `docs/arch/topics-and-provisioning.md`, `docs/arch/messaging.md` (was `ACCEPTED` before the review,
  which found its `request_id` definition stale now that a non-gateway service can mint one, and no
  publish-failure story for an originating scheduled task — re-accepted here). Also touched:
  `docs/use-cases/README.md` (a use case need not originate at the gateway; terminal-delivery mode
  is now a required section). The review resolved six edge cases in-doc — dispatch ordering vs. the
  registry-miss discard, duplicate suppression on the unsolicited path, subscription-name collision,
  schema attachment for a new topic, terminal-delivery DLQ visibility, and shared-topic/instance
  blast radius — and deferred the rest to `docs/backlog.md`.

## Services

Cell values: `NOT_STARTED` → `DRAFT` → `IN_REVIEW` → `ACCEPTED` (Design mirrors the service's
`architecture.md` frontmatter; Implementation mirrors its accepted diff). `IN_PROGRESS` = partially
built with gaps tracked in `docs/backlog.md`. `REOPENED` = was `ACCEPTED` but a later system-level
change invalidated part of that design/implementation — treat as needing a fresh
`service-architect`/`implementer` pass on the reopened scope before it can count as `ACCEPTED`
again.

| Service | Design (service-architect) | Implementation (implementer) | Docs root |
|---|---|---|---|
| `gateway-svc` | ACCEPTED¹ | IN_PROGRESS² | `gateway-svc/docs/` (design currently in system docs + `gateway-svc/AGENTS.md`) |
| `claude-automator` | REOPENED³ | REOPENED³ | `claude-automator-dev/docs/` |
| `article-svc` | NOT_STARTED (unblocked)⁴ | NOT_STARTED | `article-svc/docs/` (empty scaffold; raw notes at `article-svc/docs/draft-notes.md`) |
| `weather-svc` | NOT_STARTED | NOT_STARTED | `weather-svc/docs/` (empty scaffold) |

¹ No separate `gateway-svc/docs/architecture.md` yet — design is in the system-level `docs/` and
  `gateway-svc/AGENTS.md`. A future `service-architect` pass could formalize it under
  `gateway-svc/docs/`. The article-summary system design adds a new obligation not yet reflected in
  any `gateway-svc` design doc: gateway-initiated (unsolicited) delivery (see `docs/architecture.md`
  and `docs/backlog.md`) — pick this up when `gateway-svc`'s design is next formalized or reopened.
  The 2026-09-05 edge-case review added two specific requirements under that obligation:
  delivery-mode dispatch must run *before* the registry lookup (otherwise every unsolicited message
  is discarded as an unknown `request_id`), and this path needs its own duplicate suppression by
  `request_id`. Both are detailed in `docs/backlog.md`.
² Q&A flow runs end-to-end (see smoke tests); graceful-shutdown drain/force-fail and per-use-case
  timeout config are deferred (`docs/backlog.md`).
³ Was ACCEPTED (QA-only) and live/deployable. Reopened by the article-summary system design, which
  now specifies claude-automator as multi-use-case-aware (see `docs/architecture.md`
  "claude-automator: multi-use-case dispatch") rather than one deployment per use case. Needs a
  `service-architect` pass, then an `implementer` pass, before `article-svc`'s use case can run
  end-to-end — see `docs/backlog.md` for exactly what's still open.
⁴ **Unblocked for design as of 2026-09-05** — the article-summary system design is `ACCEPTED`, so
  `@service-architect article-svc` can start now (it reads `article-svc/docs/draft-notes.md` for the
  service-internal detail and the Pub/Sub wiring sketch left out of the system docs). Its
  *end-to-end flow* still depends on claude-automator's reopened design/implementation (³) and on the
  new gateway capability (gateway-initiated unsolicited delivery, see `docs/backlog.md`), but
  neither is `service-architect`/`implementer` work for `article-svc` itself and neither blocks its
  design pass.

## Up next

<!-- newest note last, appended on acceptance -->
- Pipeline scaffolding established. Next candidates: `weather-svc` design (`@service-architect
  weather-svc`) once its use case exists at system level, or formalizing `gateway-svc`'s design under
  `gateway-svc/docs/`.
- Article-summary workflow drafted at system level (`docs/use-cases/article-summary.md`,
  `status: DRAFT`, pending review). Once `ACCEPTED`: `@service-architect article-svc` can start
  (reads `article-svc/docs/draft-notes.md` for the service-internal detail left out here); see the
  Services table footnotes for what else unblocks (`claude-automator` is the likely long pole).
- Edge-case review pass on the article-summary system design (2026-09-05) — six gaps resolved
  in-doc, four deferred to `docs/backlog.md` (DLQ-depth alerting, message-size bounding, dedup
  durability across a gateway restart, and the manually maintained schema-topic list).
- **Article-summary system design ACCEPTED (2026-09-05.)** All four docs are now `ACCEPTED`:
  `docs/architecture.md`, `docs/arch/topics-and-provisioning.md`, `docs/arch/messaging.md`,
  `docs/use-cases/article-summary.md`. Phase 1 is closed for this workflow.
  **Next: `@service-architect article-svc`** — the immediate unblocked step; it should start from
  `article-svc/docs/draft-notes.md` (dependencies, startup/shutdown, the two flows, and the Pub/Sub
  wiring sketch), and fold what survives into `article-svc/docs/architecture.md`, deleting the
  draft-notes file per the `AGENTS.md` convention.
  Also unblocked and likely the long pole for running this flow end-to-end:
  `@service-architect claude-automator` (REOPENED³ — multi-use-case dispatch), and formalizing
  `gateway-svc`'s design to pick up gateway-initiated unsolicited delivery (see footnote ¹, and the
  two easy-to-miss requirements recorded in `docs/backlog.md`: dispatch-before-registry-lookup and
  duplicate suppression by `request_id`).
