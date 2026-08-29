# Deploying claude-automator

A manual runbook — commands you run yourself, not a script. See
[`../architecture.md`](../architecture.md) for what this process does.

The container gives `claude` a real TTY via `tmux` — watched/interactive, like `run.ts` on a bare
VM, not headless. **`tmux attach` (step 5) is the only way to observe it; `docker logs` shows
nothing**: the real process lives in a `docker exec`'d tmux pane, outside Docker's log capture.

## Part A — one-time infra setup

Skip on routine redeploys.

### 1. Prerequisites

- GCP service-account JSON key, scoped to publish on `claude-automator-responses` and
  pull/ack on `claude-automator-gateway-requests-sub` and
  `claude-automator-weather-svc-results-sub`.
- Docker on the target host.
- A running OTLP metrics collector endpoint (`config.ts` exits at startup without one).
- A Claude Console API key — added later, interactively (step 4), never stored in this repo or in
  Docker's own config.

### 2. Provision Pub/Sub infrastructure

```bash
GCP_PROJECT_ID=<your-project> ./scripts/provision-pubsub.sh
```

Idempotent. `gateway-svc/scripts/provision-pubsub.sh` (owns `gateway-requests`, the QA target) and
`weather-svc/scripts/provision-pubsub.sh` (owns `weather-svc-results`, the WEATHER target) must both
run first — see [`../../../docs/use-cases/weather.md`](../../../docs/use-cases/weather.md)'s
"Provisioning run order" and
[`../../../docs/arch/topics-and-provisioning.md`](../../../docs/arch/topics-and-provisioning.md).

## Part B — deploy / redeploy

### 1. Build

```bash
docker build -t claude-automator claude-automator-dev/claude-automator
```

Run from the repo root. Builds from local build context — no GitHub clone — so this builds
whatever's on disk, including uncommitted changes.

### 2. Run

```bash
docker run -d --name claude-automator \
  -v /path/to/your/gcp-key.json:/secrets/gcp-key.json:ro \
  -e GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcp-key.json \
  -e GCP_PROJECT_ID=<your-project> \
  -e PUBSUB_TOPIC_ID=claude-automator-responses \
  -e PUBSUB_SUBSCRIPTION_ID=claude-automator-gateway-requests-sub \
  -e PUBSUB_WEATHER_SUBSCRIPTION_ID=claude-automator-weather-svc-results-sub \
  -e OTLP_METRICS_URL=<your-otlp-collector-url> \
  -e POLL_INTERVAL_MS=5000 \
  -e POLL_COUNT=360 \
  claude-automator
```

The GCP key is a bind-mounted file, not an env var — `-e` values are visible via `docker
inspect`/shell history. `UUID_PATH`/`ACK_ID_PATH`/`USE_CASE_PATH`/`PID_FILE` already default
correctly (baked into the image); pass `-e` only to relocate them. No `ANTHROPIC_API_KEY` here —
that's step 4.

### 3. Confirm it's up

```bash
docker ps
```

Should show `claude-automator` running. Nothing is happening inside it yet.

### 4. Start the interactive session (manual, by design)

```bash
docker exec -it claude-automator tmux new -s automator
```

Inside the pane:

```bash
echo "ANTHROPIC_API_KEY=sk-ant-..." >> .env
npm start
```

Approve the first-run workspace-trust prompt as you would locally. Detach with `Ctrl-B` `D` — the
loop keeps running.

### 5. Watch or interact at any time

```bash
docker exec -it claude-automator tmux attach -t automator
```

### 6. Smoke test

See [`smoke-test/README.md`](smoke-test/README.md).

## Operational notes

A container restart kills the tmux session and the loop with it — the API key was never
persisted, so step 4 must be redone after any restart. (Future option, not implemented here:
bind-mount a host `.env` if that becomes a problem.)
