# Smoke test

Two scripts, one per use case claude-automator consumes. Each sends one message through the real
Pub/Sub path and waits for the answer, confirming a deployment (see [`../README.md`](../README.md))
works end-to-end for that use case.

Run from an operator's own authenticated `gcloud`/ADC session, **not** from inside the deployed
container: this needs subscription create/delete rights, broader than what the deployed service
itself needs (publish/pull/ack only).

## Usage: QA

```bash
export GCP_PROJECT_ID=<your-project>
npx tsx smoke-test.mts "What is the capital of France?"
```

Publishes a `QA`/`ASKED` envelope to `gateway-requests`, as the gateway itself would, and waits for
the matching `QA`/`ANSWERED` reply on `claude-automator-responses`.

## Usage: WEATHER

```bash
export GCP_PROJECT_ID=<your-project>
npx tsx smoke-test-weather.mts "Summarize this forecast in one sentence." '{"tempCelsius":31}'
```

Publishes a `WEATHER`/`FETCHED` envelope directly to `weather-svc-results`, standing in for
weather-svc, and waits for the matching `WEATHER`/`ANSWERED` reply on `claude-automator-responses`.
weather-svc need not be deployed: per `docs/PIPELINE.md`'s "Deployment order", claude-automator must
accept `WEATHER`/`FETCHED` *before* weather-svc goes live, and this exercises that path ahead of it.
Both arguments are optional (default: a canned prompt + weather JSON). `weather-svc-results` must
already exist — run `weather-svc/scripts/provision-pubsub.sh` first (see
[`../../../../docs/use-cases/weather.md`](../../../../docs/use-cases/weather.md)'s "Provisioning
run order").

## Common behavior

Both scripts subscribe to `claude-automator-responses` *before* publishing, so a fast answer can't
be missed — a subscription doesn't receive messages published before it existed. Each polls up to
two minutes (`SMOKE_TEST_TIMEOUT_MS` to override); its temporary subscription is deleted on exit and
never touches production traffic, since Pub/Sub delivers an independent copy per subscription.

If either times out: confirm the container is up and the loop is running (`../README.md` Part B,
step 5), the relevant services' `provision-pubsub.sh` have been run (see each script's own "Usage"
section above for which), and the container's `GCP_PROJECT_ID`/topic/subscription env vars match
what you're testing against.

If either fails immediately with `IAM_PERMISSION_DENIED` on `pubsub.subscriptions.create`: check
`echo $GOOGLE_APPLICATION_CREDENTIALS` in your shell — if set, it overrides your own `gcloud` ADC
for this script's Node client, and a service-account key meant for the deployed service itself
usually lacks subscription create/delete rights. Run with it unset:
`env -u GOOGLE_APPLICATION_CREDENTIALS npx tsx smoke-test.mts "..."` (or `smoke-test-weather.mts`).
