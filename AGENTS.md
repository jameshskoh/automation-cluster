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
- **Known gaps & doc-vs-code reconciliation** — [`docs/to-do.md`](docs/to-do.md): what's deferred
  and where the code diverges from the design. Check before assuming the code matches the docs.
- **Use cases** — [`docs/use-cases/`](docs/use-cases/): one file per async flow (see `README.md`
  for the required format; `qa.md` is the only live use case).
- **The gateway module** — [`gateway-svc/AGENTS.md`](gateway-svc/AGENTS.md): clean-architecture
  module rules, package/naming conventions, build & run commands.

## Provisioning

Pub/Sub infra is provisioned **per service, not per use case** — each service's
`scripts/provision-pubsub.sh` creates only what it owns, sourcing shared helpers from
[`scripts/pubsub-lib.sh`](scripts/pubsub-lib.sh). Run order matters across services; see each
script's header. Details in [`docs/arch/topics-and-provisioning.md`](docs/arch/topics-and-provisioning.md).

## How work flows here

- Design changes start in `docs/architecture.md` (or its `arch/` detail docs); record
  deferrals/divergence in `docs/to-do.md`.
- A new use case = a new `docs/use-cases/<name>.md` (from the template) + per-service provisioning
  edits + code.
