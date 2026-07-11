# Smoke test

Sends one question through the real Pub/Sub path and waits for claude-automator's answer —
confirms a deployment (see [`../README.md`](../README.md)) works end-to-end.

Run from an operator's own authenticated `gcloud`/ADC session, **not** from inside the deployed
container: `wait-for-response.ts` needs subscription create/delete rights, broader than what the
deployed service itself needs (publish/pull/ack only).

## Usage

```bash
export GCP_PROJECT_ID=<your-project>

npx tsx send-message.ts "What is the capital of France?"
# prints a request_id and the exact next command to run

npx tsx wait-for-response.ts <request_id>
```

`wait-for-response.ts` polls for up to two minutes (`SMOKE_TEST_TIMEOUT_MS` to override) via a
temporary subscription on `claude-automator-responses`, deleted again on exit. It doesn't affect
production traffic — Pub/Sub delivers an independent copy of every message per subscription.

If it times out: confirm the container is up and the loop is running (`../README.md` Part B, step
5), both services' `provision-pubsub.sh` have been run, and the container's `GCP_PROJECT_ID`/
topic/subscription env vars match what you're testing against.
