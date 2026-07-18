# Pipeline status

Read first, every session — the durable, cross-session record of what's done and what's unblocked.
Updated by `system-architect`, `service-architect`, and `implementer` on `status: ACCEPTED` (see the
`AGENTS.md` "Sub-agent workflow" section). Safe to hand-edit for a new service row or a stale entry.

This is the per-phase/per-service summary; per-artifact detail lives in each doc's `status:`
frontmatter. `docs/backlog.md` is separate — deferred work and doc-vs-code divergence, not progress.

## System design

- status: ACCEPTED (pre-existing — `docs/architecture.md`, `docs/arch/*`, `docs/use-cases/qa.md`)

## Services

Cell values: `NOT_STARTED` → `DRAFT` → `IN_REVIEW` → `ACCEPTED` (Design mirrors the service's
`architecture.md` frontmatter; Implementation mirrors its accepted diff). `IN_PROGRESS` = partially
built with gaps tracked in `docs/backlog.md`.

| Service | Design (service-architect) | Implementation (implementer) | Docs root |
|---|---|---|---|
| `gateway-svc` | ACCEPTED¹ | IN_PROGRESS² | `gateway-svc/docs/` (design currently in system docs + `gateway-svc/AGENTS.md`) |
| `claude-automator` | ACCEPTED | ACCEPTED³ | `claude-automator-dev/docs/` |
| `weather-svc` | NOT_STARTED | NOT_STARTED | `weather-svc/docs/` (empty scaffold) |

¹ No separate `gateway-svc/docs/architecture.md` yet — design is in the system-level `docs/` and
  `gateway-svc/AGENTS.md`. A future `service-architect` pass could formalize it under `gateway-svc/docs/`.
² Q&A flow runs end-to-end (see smoke tests); graceful-shutdown drain/force-fail and per-use-case
  timeout config are deferred (`docs/backlog.md`).
³ Live, deployable (Docker); remaining gaps (no tests, disk-based correlation, "nothing to do"
  mislabeled as failure) tracked in `docs/backlog.md`.

## Up next

<!-- newest note last, appended on acceptance -->
- Pipeline scaffolding established. Next candidates: `weather-svc` design (`@service-architect
  weather-svc`) once its use case exists at system level, or formalizing `gateway-svc`'s design under
  `gateway-svc/docs/`.
