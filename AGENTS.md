# automation-cluster

Mono-repo of independently deployable services that process requests asynchronously via GCP
Pub/Sub. One always-on stateful **gateway** (`gateway-svc`) receives a request, publishes to
Pub/Sub, and holds the caller's connection open until an answer message returns; everything else
is event-driven.

## Where things are

- **System design** — [`docs/architecture.md`](docs/architecture.md): the gateway, functions,
  graceful shutdown, accepted risks. Start here. Its detail docs live in
  [`docs/arch/`](docs/arch/): [`messaging.md`](docs/arch/messaging.md) (the message envelope +
  schema rules) and [`topics-and-provisioning.md`](docs/arch/topics-and-provisioning.md)
  (topic/subscription/DLQ ownership + the provisioning-script convention).
- **Known gaps & doc-vs-code reconciliation** — [`docs/backlog.md`](docs/backlog.md): what's deferred
  and where the code diverges from the design. Check before assuming the code matches the docs.
- **Use cases** — [`docs/use-cases/`](docs/use-cases/): one file per async flow (see `README.md`
  for the required format; `qa.md` is the only live use case).
- **The gateway module** — [`gateway-svc/AGENTS.md`](gateway-svc/AGENTS.md): clean-architecture
  module rules, package/naming conventions, build & run commands.
- **Deployment guides** — per-service, see "Deployment guides" below.

## Deployment guides

Deployment guides live per-service under `<service>/docs/deploy/`, not centralized under the
repo-root `docs/`, since ownership of how a service deploys sits with that service and a service
may need more than one such doc (e.g. one per environment or deploy path). Example: a deploy guide
for `gateway-svc` would live at `gateway-svc/docs/deploy/`.

Exception: `claude-automator`'s docs (including `docs/deploy/`) live at
`claude-automator-dev/docs/`, a sibling of its code at `claude-automator-dev/claude-automator/` —
see `claude-automator-dev/AGENTS.md`.

## Provisioning

Pub/Sub infra is provisioned **per service, not per use case** — each service's
`scripts/provision-pubsub.sh` creates only what it owns, sourcing shared helpers from
[`scripts/pubsub-lib.sh`](scripts/pubsub-lib.sh). Run order matters across services; see each
script's header. Details in [`docs/arch/topics-and-provisioning.md`](docs/arch/topics-and-provisioning.md).

## How work flows here

- Design changes start in `docs/architecture.md` (or its `arch/` detail docs); record
  deferrals/divergence in `docs/backlog.md`.
- A new use case = a new `docs/use-cases/<name>.md` (from the template) + per-service provisioning
  edits + code.

## Sub-agent workflow

Three chained sub-agents in `.claude/agents/`, invoked one at a time with a human gate between
phases. This section loads into every sub-agent's context, so shared rules live here once, not per
sub-agent file.

### Phases and the gate

| Phase | Sub-agent | Writes | Gate to advance |
|---|---|---|---|
| 1. System design | `system-architect` | `docs/architecture.md`, `docs/arch/*.md`, `docs/use-cases/*.md` (per `docs/use-cases/README.md` format) | User accepts → `status: ACCEPTED` |
| 2. Service design | `service-architect` (once per service) | `<docs-root>/architecture.md` (+ `arch/`, `use-cases/`) for one service | Same, per service. Requires phase 1 ACCEPTED |
| 3. Implementation | `implementer` (once per service) | Code for that service | User accepts the diff. Requires that service's phase-2 doc ACCEPTED |

**Never start a phase until the prior phase's doc is `status: ACCEPTED`.** To check a phase's state,
read its `status:` field and `docs/PIPELINE.md` — don't infer from conversation history.

### `status:` convention

Design docs start with:
```yaml
---
status: DRAFT   # DRAFT -> IN_REVIEW -> ACCEPTED
---
```
Applies to `architecture.md`, `arch/*`, and `use-cases/*.md`, system-level and per-service. Not to
`docs/use-cases/README.md`, `docs/backlog.md`, `docs/PIPELINE.md`, or `docs/deploy/`. Phase-3 code
carries no frontmatter — its acceptance is the row flip in `docs/PIPELINE.md`.

### Doc-location rule

A service's docs default to `<service>/docs/`. Resolve the docs root from this table before reading
or writing:

| Service | Docs root |
|---|---|
| `claude-automator` | `claude-automator-dev/docs/` (sibling of its code at `claude-automator-dev/claude-automator/`) |

### Branch gate

Verify the current branch is **not `main`** before writing — work happens on a `dev` branch (e.g.
`dev/<topic>`). Stop and ask if on `main`.

### Invocation

- Use `@subagent-name` — the only method guaranteed to run that exact sub-agent (not a
  description-match guess).
- Confirm the prior phase is `ACCEPTED` (via `docs/PIPELINE.md` and the target doc's `status:`)
  before invoking.
- Ambiguous request, no sub-agent named? Check `docs/PIPELINE.md` and the doc's `status:`; still
  unclear → ask.

### Plan-mode

All three run `permissionMode: plan`: start read-only, propose, call `ExitPlanMode` for approval
before writing, then continue read/write in the same run. Requires the **main session** to be in
`default` or `plan` mode — `acceptEdits`/`bypassPermissions` on the parent overrides it.

### Shared rules

- **Commits are manual.** On acceptance, set `status: ACCEPTED` (or flip the `PIPELINE.md` row for
  phase 3), suggest a message, and stop — never run `git add`/`git commit`:
  - `docs(system): <summary>` — phase 1
  - `docs(<service>): <summary>` — phase 2
  - `feat(<service>): <summary>` — phase 3
- **No independent research.** A gap needing outside knowledge means stop and ask for a usage guide,
  not guess.
- **Enforcement is by convention**, not platform-enforced — a hard `PreToolUse`-hook layer is
  tracked in `docs/backlog.md`.
