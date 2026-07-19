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
| `gateway-svc` | ACCEPTED¹ | IN_PROGRESS² | `gateway-svc/docs/` |
| `claude-automator` | ACCEPTED (QA + WEATHER)³ | ACCEPTED³ (QA + WEATHER) | `claude-automator-dev/docs/` |
| `weather-svc` | ACCEPTED⁴ | NOT_STARTED | `weather-svc/docs/` |

¹ Now formalized: `gateway-svc/docs/architecture.md` ACCEPTED (backfills the as-built QA gateway +
  layers the WEATHER pass), alongside `use-cases/qa.md` and `use-cases/weather.md` (both ACCEPTED).
  Carries a T1–T6 WEATHER phase-3 breakdown; `@implementer gateway-svc` can run once "Deployment
  order" allows. The abstract gateway model still lives in the system-level `docs/` +
  `gateway-svc/AGENTS.md`, which the service doc references rather than restates.
² Q&A flow runs end-to-end (see smoke tests); graceful-shutdown drain/force-fail and per-use-case
  timeout config are deferred (`docs/backlog.md`).
³ QA flow live, deployable (Docker); remaining gaps (no tests, disk-based correlation, "nothing to
  do" mislabeled as failure) tracked in `docs/backlog.md`. WEATHER design pass ACCEPTED
  (`claude-automator-dev/docs/architecture.md`, `arch/messaging.md`, `arch/disk-correlation.md`, new
  `use-cases/weather.md`): adds a second inbound subscription, generalizes inbound zod validation to
  `WEATHER`/`FETCHED`, adds a `USE_CASE_PATH` correlation file for outbound `use_case` pass-through +
  ack routing, and assembles prompt (`metadata`) + data (`payload`) with `<prompt>…</prompt>
  <data>…</data>` tags. QA polled before WEATHER each round (accepted possible-starvation risk,
  documented). T1–T4 phase-3 breakdown now built: `config.ts`/`.env.example`/`Dockerfile` carry the
  new `PUBSUB_WEATHER_SUBSCRIPTION_ID`/`USE_CASE_PATH` vars (T1); `pubsub-client.ts` generalizes the
  inbound schema to a discriminated union, polls both subscriptions each round (QA first), assembles
  prompt/data for WEATHER, and routes the outbound publish/ack by the inbound `use_case` (T2);
  `scripts/provision-pubsub.sh` provisions the new WEATHER subscription + DLQ (T3); deploy docs and
  a second smoke-test script (`smoke-test-weather.mts`, publishing directly to `weather-svc-results`
  since weather-svc doesn't exist yet) cover the WEATHER path (T4). Deployment to production is a
  separate, not-yet-done operational step — see "Deployment order" below, still gating weather-svc.
⁴ Phase-2 design ACCEPTED — `weather-svc/docs/architecture.md` (+ `arch/open-meteo-integration.md`,
  `arch/messaging.md`, `use-cases/weather.md`). Plain Java 21 Cloud Run function (no Spring Boot),
  `HttpFunction` behind a Pub/Sub push subscription, open-meteo geocode+forecast with 4×6h block
  aggregation, `FETCHED`/`FAILED` error short-circuit. Carries a 7-task (T1–T7) phase-3 breakdown, so
  `@implementer weather-svc` can run. See "Deployment order" below.

## Deployment order

Cross-service deploy-**ordering** constraints — distinct from the per-service deploy guides under
`<service>/docs/deploy/`, which cover *how* to deploy one service. A constraint here belongs to no
single service, so it lives in this read-first index.

- **claude-automator before weather-svc.** claude-automator must be deployed accepting
  `WEATHER`/`FETCHED` before weather-svc goes live. Until then, weather-svc's `FETCHED` messages
  nack → DLQ at claude-automator and surface only as a gateway timeout (the error short-circuit
  covers weather-svc's own `FAILED`, not a claude-automator inbound rejection). Tracked as an
  accepted-risk in `claude-automator-dev/docs/architecture.md`.

## Up next

<!-- newest note last, appended on acceptance -->
- Pipeline scaffolding established. Next candidates: `weather-svc` design (`@service-architect
  weather-svc`) once its use case exists at system level, or formalizing `gateway-svc`'s design under
  `gateway-svc/docs/`.
- WEATHER use case ACCEPTED (`docs/use-cases/weather.md`). Downstream work now unblocked across
  three fronts — weather-svc (new service), a claude-automator revisit, and gateway phase-3 —
  detailed per-phase in the WEATHER technical notes below.
- weather-svc phase-2 design ACCEPTED (`weather-svc/docs/`). Plain Java 21 Cloud Run function + push
  subscription; carries a T1–T7 phase-3 breakdown. Next: `@implementer weather-svc` — but honor
  "Deployment order" (claude-automator before weather-svc). claude-automator and gateway phase-3
  remain per the WEATHER technical notes.
- claude-automator WEATHER design pass DRAFT (`@service-architect claude-automator`) — service docs
  adapted to the WEATHER inbound flow (second subscription, generalized validation, `USE_CASE_PATH`
  pass-through, prompt/data XML assembly) with a T1–T4 phase-3 breakdown. Next: user review →
  ACCEPTED, then `@implementer claude-automator` (respecting "Deployment order"). Two
  backlog entries proposed for the coordinator to apply to `docs/backlog.md` (outside the service's
  write-lane).
- gateway-svc and claude-automator WEATHER designs both ACCEPTED (user review). gateway-svc's
  as-built design also formalized under `gateway-svc/docs/` (was previously only in system docs +
  `AGENTS.md`); the QA-before-WEATHER poll-order starvation risk is now documented as an accepted risk
  in claude-automator's `architecture.md`. All three service designs for WEATHER (gateway-svc,
  weather-svc, claude-automator) are now ACCEPTED — phase 3 is unblocked for all three, gated only by
  "Deployment order" (claude-automator before weather-svc goes live). Next: `@implementer <service>`.
- `@implementer claude-automator` ran the WEATHER T1–T4 breakdown — Implementation column now
  ACCEPTED (QA + WEATHER). Code built, not yet deployed: redeploying claude-automator (see
  `claude-automator-dev/docs/deploy/README.md`) is the remaining step before "Deployment order"
  allows weather-svc to go live. Next: `@implementer weather-svc` (build first; still honor
  "Deployment order" for go-live) and/or `@implementer gateway-svc` for its WEATHER T1–T6.

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
  DLQ and surface only as a timeout — this is the "Deployment order" constraint above); consume
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
