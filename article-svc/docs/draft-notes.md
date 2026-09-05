# Draft notes (pre-service-architect)

See the `draft-notes.md` convention in the root `AGENTS.md`. Raw notes from the user, captured
2026-09-05, to feed `@service-architect article-svc` once system design for this workflow is
`ACCEPTED`.

## Dependencies

- An RSS service (1 supplier for now). Its codebase is not controlled by us, but we deploy it.
  - Feature 1: serves subscription URLs via OPML (XML).
  - Feature 2: when a subscription URL is accessed, returns the article content wrapped in XML,
    usable immediately.
- article-db: holds the RSS service's subscription URLs (and presumably fetched-article state).

## Startup flow

1. Read and validate `application.yaml` config.
2. Connect to Google Secret Manager to resolve secrets in the config.
3. Attempt to connect to article-db.

## Flow 1: sync subscription URLs

- Periodically read from the RSS service to retrieve updated subscription URLs.
- Request the OPML file (XML, known structure ahead of time).
- Parse it and save subscription links into a (new) table in article-db.

## Flow 2: fetch + summarize + forward

- Periodically query subscription URLs from article-db.
- For each subscription URL:
  - Execute the request, get the result.
  - Parse the result into multiple articles.
  - For each article, forward to claude-automator (via message queue) with a hard-coded
    "summarize" prompt.

## Shutdown flow

Trivial — service is largely stateless. Not elaborated further by the user.

## Pub/Sub wiring sketch (from the system-level edge-case review)

Concrete names and script changes for the `ARTICLE_SUMMARY` use case
(`docs/use-cases/article-summary.md`), sketched at review time for `article-svc`'s (and
claude-automator's / gateway-svc's) `service-architect`/`implementer` passes to finalize:

Note the DLQ-ownership split the existing scripts already follow: a DLQ topic is *created* by the
script of the service that owns the application topic it backstops, and merely *referenced* by the
consuming service's script when it creates its subscription. (E.g. today `gateway-svc` creates
`gateway-requests-claude-automator-sub-dlq`, and claude-automator's script passes that name when
creating its subscription.) Matches `docs/arch/topics-and-provisioning.md`: each script creates
"the topic(s) + DLQ(s) that service publishes to."

- New topic: `article-svc-requests` (owned/published by `article-svc`), provisioned by
  `article-svc/scripts/provision-pubsub.sh` — which also creates the DLQ backstopping
  claude-automator's subscription on it, `article-svc-requests-claude-automator-sub-dlq`.
- claude-automator adds a new owned subscription against it:
  `claude-automator-article-svc-requests-sub`, filter
  `use_case="ARTICLE_SUMMARY" AND stage="SUMMARIZE_REQUESTED"`, referencing the DLQ above — added in
  `claude-automator-dev/claude-automator/scripts/provision-pubsub.sh` alongside the existing QA
  subscription block (additive, that block untouched).
- `claude-automator-responses` (existing topic) gets a second new subscription, owned by the
  gateway: `gateway-claude-automator-responses-article-summary-sub`, filter
  `use_case="ARTICLE_SUMMARY" AND stage="SUMMARIZED"` — added in
  `gateway-svc/scripts/provision-pubsub.sh` alongside the existing `QA`-filtered subscription
  (which keeps its current, undisambiguated name — see `docs/arch/topics-and-provisioning.md`'s
  disambiguator rule). Its DLQ, `claude-automator-responses-gateway-article-summary-sub-dlq`, is
  created by claude-automator's script (owner of that topic), per the split above.
- Run order: `article-svc`'s script before claude-automator's (its new subscription targets
  `article-svc`'s new topic and its new DLQ). claude-automator's before `gateway-svc`'s (the
  gateway's new subscription references a DLQ claude-automator's script creates) — the same
  direction as today's QA ordering, so no existing header comment reverses.
- After the new topic exists, re-run the repo-root `scripts/provision-pubsub-schema.sh` with
  `article-svc-requests` added to its `TOPICS_USING_SCHEMA` list (schema attachment is
  repo-root-owned, not per-service — see `docs/arch/topics-and-provisioning.md`). Easy to miss:
  the topic is created and carries traffic fine without this step, just unvalidated.
- Neither claude-automator nor gateway-svc needs a new deploy unit or a second instance/image for
  this — additive subscription blocks on an existing running service.
