---
status: ACCEPTED
---

# Topics, subscriptions, and provisioning

How the [messaging envelope](messaging.md) is carried on GCP Pub/Sub: topic/subscription/DLQ
ownership and naming, and the per-service provisioning-script convention. See
[`../architecture.md`](../architecture.md) for the system model.

## Topics and subscriptions

- **A topic is owned by, and named for, its publisher** — not any particular consumer. A topic
  may (and often will) have multiple subscriptions, each belonging to a different consumer, each
  with its own attribute-based filter for the `use_case`/`stage` combinations that consumer cares
  about.
- **A subscription is owned by its consumer.** Adding a new use case to an existing publisher
  means adding a new subscription (with the appropriate filter) against that publisher's existing
  topic — it does not require creating a new topic.
- Convention: **1 topic per publishing service** (not per service+use_case). The use_case/stage
  attributes on the subscription do the filtering work; the same fields in the message body
  remain useful for logging and for any consumer that reads multiple use cases off one
  subscription.
- Naming convention: topic = `<owning-publisher>-<purpose>` (e.g. a function's own outbound
  topic is named after that function, not after whoever reads it). Subscription =
  `<consuming-service>-<topic-name>-sub`.
  - **Multiple subscriptions from one consumer on one topic** collide under the base name (each
    needs a distinct `use_case`/`stage` filter, since filters are immutable). Disambiguate by
    inserting the use_case: `<consumer>-<topic-name>-<use_case>-sub` (DLQ:
    `<topic>-<consumer>-<use_case>-sub-dlq`). Apply the longer form only to the *additional*
    subscription(s); a pre-existing one keeps its shorter name (renaming means delete-and-recreate).
    Example: the gateway consumes `claude-automator-responses` for both QA and WEATHER — QA stays
    `gateway-claude-automator-responses-sub`, WEATHER is
    `gateway-claude-automator-responses-weather-sub`.
- DLQ convention: **one DLQ topic per subscription that needs one**, named `<topic>-<sub>-dlq`
  (GCP Pub/Sub's dead-letter feature is configured per-subscription; there is no built-in global
  DLQ primitive). This keeps a failure signal next to the stage that produced it and makes
  replay/redrive straightforward without needing to inspect attributes to figure out which use
  case failed.
  - Note the ownership difference this name encodes: an *application* topic is publisher-owned and
    publisher-named (see above), but a *DLQ* topic is owned by the **subscription** it backstops —
    hence the `<sub>` in its name. That is deliberate, not a violation of the one-topic-per-publisher
    rule: because dead-lettering is attached per-subscription, two consumers filtering the same
    topic must not share one graveyard, or you couldn't tell which consumer failed or replay just
    its stream. The DLQ topic is published to by the Pub/Sub service agent (see the IAM grants in
    the provisioning scripts), never by one of your services, so it never becomes a service's second
    outbound topic.

## Provisioning scripts

Provisioning is organized **per service, not per use case** — even though a use case's topics and
subscriptions span multiple services, a single script covering an entire use case would need to
create infrastructure it doesn't own (e.g. the gateway's script creating claude-automator's
topic), which breaks the ownership model above. A use case can also reuse an existing topic
rather than requiring a new one, so "one script per use case" doesn't cleanly map to "one thing to
provision" anyway.

Instead, each service has its own `scripts/provision-pubsub.sh` that provisions exactly:

1. The topic(s) + DLQ(s) that service publishes to (infrastructure it owns).
2. That service's own subscription(s) against topics owned by other services (a subscription is
   always owned by its consumer, never by the topic's publisher).

The actual `gcloud` calls (create-topic-if-missing, create-subscription-if-missing, DLQ IAM
grants) are common across every service's script, so they live once in `scripts/pubsub-lib.sh` at
the repo root. Each service's `provision-pubsub.sh` sources that library and calls its functions
with the topic/subscription/filter names specific to that service — the per-service script is
just a declaration of *which* topics and subscriptions to create, not how to create them.

Consequences of this split, worth knowing before running these scripts:

- **Run order matters across services.** Since a service's subscription targets another service's
  topic, that topic must already exist. For a new use case spanning services A and B, run A's
  script before B's if B subscribes to A's topic (or vice versa) — each script's header comments
  state its specific dependency.
- **Idempotent for additions, not for modifications.** Each script checks for existing
  topics/subscriptions via `gcloud ... describe` before creating, so re-running after adding a new
  topic/subscription block is safe — existing entries are skipped, only new ones are created.
  However, editing an *existing* subscription's filter in place will not take effect: the
  `describe` check sees the subscription already exists and skips it, silently leaving the old
  filter live, because **Pub/Sub subscription filters are immutable after creation**. Changing a
  filter requires deleting and recreating the subscription.
- **Cross-service dependency cycles are possible.** With three or more services each publishing a
  topic another consumes, no single linear run order may satisfy every
  topic-before-its-subscription constraint — e.g. the gateway owns `gateway-requests` (weather-svc
  subscribes) yet also subscribes to `weather-svc-results`, so its script must run both before and
  after weather-svc. Because the scripts are idempotent (skip-existing), a second pass breaks the
  cycle: create the topics first, then re-run the entry-point script so its subscriptions find their
  targets. A topics-first/subs-second split of each script would let a single sweep work instead —
  left to the script author, not mandated here. WEATHER is the first to hit this; see
  [`../use-cases/weather.md`](../use-cases/weather.md)'s run-order section.
- The use-case doc (see [`../use-cases/README.md`](../use-cases/README.md)) is still the place a
  developer looks to see the *whole* cross-service picture (which topics/subscriptions/filters
  exist for this use case) — it links out to whichever services' scripts actually provision each
  piece, rather than owning the provisioning itself.
