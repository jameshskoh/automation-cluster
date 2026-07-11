# Smoke test

Sends one question through the real Pub/Sub path and waits for claude-automator's answer —
confirms a deployment (see [`../README.md`](../README.md)) works end-to-end.

Run from an operator's own authenticated `gcloud`/ADC session, **not** from inside the deployed
container: this needs subscription create/delete rights, broader than what the deployed service
itself needs (publish/pull/ack only).

## Usage

```bash
export GCP_PROJECT_ID=<your-project>
npx tsx smoke-test.ts "What is the capital of France?"
```

It subscribes to `claude-automator-responses` *before* publishing the question, so a fast answer
can never be missed — Pub/Sub subscriptions don't receive messages published before they existed.
Polls for up to two minutes (`SMOKE_TEST_TIMEOUT_MS` to override); the temporary subscription is
deleted on exit and never affects production traffic, since Pub/Sub delivers an independent copy
of every message per subscription.

If it times out: confirm the container is up and the loop is running (`../README.md` Part B, step
5), both services' `provision-pubsub.sh` have been run, and the container's `GCP_PROJECT_ID`/
topic/subscription env vars match what you're testing against.

If it fails immediately with `IAM_PERMISSION_DENIED` on `pubsub.subscriptions.create`: check
`echo $GOOGLE_APPLICATION_CREDENTIALS` in your shell — if set, it overrides your own `gcloud` ADC
for this script's Node client, and a service-account key meant for the deployed service itself
usually lacks subscription create/delete rights. Run with it unset:
`env -u GOOGLE_APPLICATION_CREDENTIALS npx tsx smoke-test.mts "..."`.
