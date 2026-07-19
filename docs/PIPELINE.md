# Pipeline status

Read first, every session — the durable, cross-session record of what's done and what's unblocked.
Updated by `system-architect`, `service-architect`, and `implementer` on `status: ACCEPTED` (see the
`AGENTS.md` "Sub-agent workflow" section). Safe to hand-edit for a new service row or a stale entry.

This is the per-phase/per-service summary; per-artifact detail lives in each doc's `status:`
frontmatter. `docs/backlog.md` is separate — deferred work and doc-vs-code divergence, not progress.

## System design

- status: ACCEPTED (pre-existing — `docs/architecture.md`, `docs/arch/*`, `docs/use-cases/qa.md`)
- WEATHER use case: `docs/use-cases/weather.md` ACCEPTED — also adds the generic error
  short-circuit convention to `docs/architecture.md` and a subscription-naming +
  cross-service-cycle note to `docs/arch/topics-and-provisioning.md`. Downstream design detail is
  parked in "WEATHER — technical notes for downstream phases" below.

## Services

Cell values: `NOT_STARTED` → `DRAFT` → `IN_REVIEW` → `ACCEPTED` (Design mirrors the service's
`architecture.md` frontmatter; Implementation mirrors its accepted diff). `IN_PROGRESS` = partially
built with gaps tracked in `docs/backlog.md`.

| Service | Design (service-architect) | Implementation (implementer) | Docs root |
|---|---|---|---|
| `gateway-svc` | ACCEPTED¹ | IN_PROGRESS² | `gateway-svc/docs/` (design currently in system docs + `gateway-svc/AGENTS.md`) |
| `claude-automator` | ACCEPTED | ACCEPTED³ | `claude-automator-dev/docs/` |
| `weather-svc` | NOT_STARTED⁴ | NOT_STARTED | `weather-svc/docs/` (empty scaffold) |

¹ No separate `gateway-svc/docs/architecture.md` yet — design is in the system-level `docs/` and
  `gateway-svc/AGENTS.md`. A future `service-architect` pass could formalize it under `gateway-svc/docs/`.
² Q&A flow runs end-to-end (see smoke tests); graceful-shutdown drain/force-fail and per-use-case
  timeout config are deferred (`docs/backlog.md`).
³ Live, deployable (Docker); remaining gaps (no tests, disk-based correlation, "nothing to do"
  mislabeled as failure) tracked in `docs/backlog.md`. The WEATHER use case adds a second inbound
  flow for claude-automator — see the WEATHER technical notes below.
⁴ Now unblocked — `docs/use-cases/weather.md` is ACCEPTED, so the WEATHER use case exists at system
  level and `@service-architect weather-svc` can run. Downstream handoff detail is in the WEATHER
  technical notes below.

## Up next

<!-- newest note last, appended on acceptance -->
- Pipeline scaffolding established. Next candidates: `weather-svc` design (`@service-architect
  weather-svc`) once its use case exists at system level, or formalizing `gateway-svc`'s design under
  `gateway-svc/docs/`.
- WEATHER use case ACCEPTED (`docs/use-cases/weather.md`). Downstream work now unblocked across
  three fronts — weather-svc (new service), a claude-automator revisit, and gateway phase-3 —
  detailed per-phase in the WEATHER technical notes below.

## WEATHER — technical notes for downstream phases

Design detail for phases below system-design altitude, parked here (PIPELINE.md is read first every
session) so it isn't lost between phases. Pointers, not designs — each phase's architect/implementer
produces the real design; the flow's source of truth is `docs/use-cases/weather.md`.

- **weather-svc** (→ `@service-architect weather-svc`, then `@implementer weather-svc`): new Cloud
  Run Function, v1 Malaysia-only (`countryCode=MY`). Geocode the city via open-meteo
  `GET /v1/search?name=<city>&countryCode=MY&count=10`, then filter client-side on `admin1` for the
  requested state (no server-side state filter exists). Caveats: fuzzy search can over-match a
  near-miss name, so verify the returned `name`; `admin1` can't disambiguate two same-named towns in
  the same state (accepted). Then fetch the forecast and publish `WEATHER`/`FETCHED` (weather JSON
  in `payload` + interpretation prompt in `metadata`), or on a city-match / forecast-retrieval
  failure publish terminal `WEATHER`/`FAILED` with a reason. Full analysis:
  `/Users/jameshskoh/knowledgebase/tools/apis/open-meteo/`.
- **claude-automator** (→ `@implementer`, against its ACCEPTED design; maybe a `@service-architect`
  touch first): add a 2nd inbound subscription (`claude-automator-weather-svc-results-sub` on
  `weather-svc-results`, filter `WEATHER AND FETCHED`); generalize the inbound zod validation beyond
  the hardcoded `QA`/`ASKED` to accept `WEATHER`/`FETCHED` (until this ships those messages nack →
  DLQ and surface only as a timeout, so it must deploy before weather-svc goes live); consume
  `payload` + `metadata` by concatenating prompt (`metadata`) + data (`payload`) with an XML-tag
  delimiter; publish `WEATHER`/`ANSWERED` on `claude-automator-responses`.
- **gateway** (→ `@implementer`): add the `/get-weather <city>, <state>` Slack command (Socket Mode,
  `/ask` ack-3s-then-`response_url` pattern); a WEATHER request service publishing
  `WEATHER`/`REQUESTED` on `gateway-requests`; two new subscriptions
  (`gateway-claude-automator-responses-weather-sub` for `WEATHER AND ANSWERED`,
  `gateway-weather-svc-results-sub` for `WEATHER AND FAILED`); generic error-stage handling (a
  `FAILED` message → resolve `request_id`, deliver failure via the `AnswerSink` path, evict the
  registry entry); per-use-case timeout generalization (per-entry `expiresAt` in the registry +
  config from flat `qa.*` to a per-use-case declaration, so WEATHER's 2-min timeout coexists with
  QA's).
- **provisioning** (→ script author): resolve the three-service dependency cycle — the documented
  two-pass run (`docs/use-cases/weather.md`'s run-order section) or a topics-first/subs-second split
  of each `provision-pubsub.sh` (open decision). Extend `scripts/provision-pubsub-schema.sh` to
  attach the shared `gateway-message` schema to `weather-svc-results`. Apply the subscription-naming
  extension (`<consumer>-<topic>-<use_case>-sub`) for the gateway's 2nd sub on
  `claude-automator-responses` — see `docs/arch/topics-and-provisioning.md`.
