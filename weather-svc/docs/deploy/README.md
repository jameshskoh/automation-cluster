# Deploying weather-svc

A manual runbook — commands you run yourself, not a script (except where noted). See
[`../architecture.md`](../architecture.md) for what this service does and its runtime/stack
choices (plain Java 21 Cloud Run function, no Dockerfile, push subscription not Eventarc).

**Per `docs/PIPELINE.md`'s "Deployment order": do not run Part C (the live deploy) until
claude-automator has been redeployed accepting `WEATHER`/`FETCHED`.** Parts A and B (build +
provisioning up to the first deploy) are safe to run any time.

## Part A — one-time infra prerequisites

```bash
gcloud services enable artifactregistry.googleapis.com cloudbuild.googleapis.com \
    run.googleapis.com pubsub.googleapis.com
```

Also needed, once, for the push subscription's authenticated invocation (see Part C, step 3):

- A dedicated service account for the push subscription to invoke weather-svc as, e.g.
  `weather-svc-push@<project>.iam.gserviceaccount.com`:
  ```bash
  gcloud iam service-accounts create weather-svc-push \
      --display-name "Pub/Sub push authenticator for weather-svc"
  ```

## Part B — provision Pub/Sub infra (pass 1, pre-deploy)

```bash
GCP_PROJECT_ID=<your-project> ./scripts/provision-pubsub.sh
```

Creates `weather-svc-results` (+ its DLQ) and the DLQ backstopping weather-svc's own subscription.
**`gateway-svc/scripts/provision-pubsub.sh` must have already run** (creates `gateway-requests`,
the topic weather-svc's subscription targets) — see
[`../../../docs/use-cases/weather.md`](../../../docs/use-cases/weather.md)'s "Provisioning run
order". The push subscription itself is **not** created yet — its endpoint is only known after the
first deploy (Part C, step 4).

## Part C — deploy

### 1. Build + deploy (source-based, no Dockerfile)

Run from the repo root — the buildpack builds from `weather-svc/` directly:

```bash
gcloud run deploy weather-svc \
  --source weather-svc \
  --function com.jameshskoh.weather.messaging.in.WeatherFunction \
  --base-image java21 \
  --region <your-region> \
  --no-allow-unauthenticated \
  --set-env-vars GCP_PROJECT_ID=<your-project>,WEATHER_RESULTS_TOPIC_ID=weather-svc-results
```

- `--no-allow-unauthenticated`: only the dedicated push service account (below) may invoke this
  service — Pub/Sub push subscriptions authenticate via an OIDC identity token when
  `--push-auth-service-account` is set.
- Other env vars (`OPENMETEO_GEOCODING_URL`, `OPENMETEO_FORECAST_URL`, `FORECAST_DAYS`,
  `HTTP_TIMEOUT_MS`) are optional — see [`../architecture.md`](../architecture.md), "Configuration",
  for their defaults.

### 2. Grant weather-svc's own runtime identity publish rights

weather-svc's runtime service account (the default compute SA unless `--service-account` was
overridden above) needs to publish `FETCHED`/`FAILED`:

```bash
gcloud pubsub topics add-iam-policy-binding weather-svc-results \
  --member=serviceAccount:<PROJECT_NUMBER>-compute@developer.gserviceaccount.com \
  --role=roles/pubsub.publisher
```

### 3. Grant the push service account permission to invoke weather-svc

```bash
gcloud run services add-iam-policy-binding weather-svc \
  --region <your-region> \
  --member=serviceAccount:weather-svc-push@<your-project>.iam.gserviceaccount.com \
  --role=roles/run.invoker
```

Pub/Sub's own service agent also needs permission to mint identity tokens *as* that push service
account (standard requirement for authenticated push, not specific to this repo):

```bash
PROJECT_NUMBER=$(gcloud projects describe <your-project> --format="value(projectNumber)")
gcloud iam service-accounts add-iam-policy-binding \
  weather-svc-push@<your-project>.iam.gserviceaccount.com \
  --member=serviceAccount:service-${PROJECT_NUMBER}@gcp-sa-pubsub.iam.gserviceaccount.com \
  --role=roles/iam.serviceAccountTokenCreator
```

### 4. Provision Pub/Sub infra (pass 2, post-deploy) — create the push subscription

Now that the Cloud Run URL is known (`gcloud run services describe weather-svc --region
<your-region> --format="value(status.url)"`), re-run the same script with the endpoint set:

```bash
GCP_PROJECT_ID=<your-project> \
  WEATHER_SVC_PUSH_ENDPOINT=<the URL from above> \
  WEATHER_SVC_PUSH_SERVICE_ACCOUNT=weather-svc-push@<your-project>.iam.gserviceaccount.com \
  ./scripts/provision-pubsub.sh
```

Idempotent — safe to re-run; only the (till-now-skipped) push subscription gets created.

### 5. Attach the shared schema (once all three services' topics exist)

```bash
GCP_PROJECT_ID=<your-project> ./scripts/provision-pubsub-schema.sh
```

Run from the repo root, after `gateway-svc`'s, `claude-automator`'s, and weather-svc's
`provision-pubsub.sh` have all run at least once (it attaches the shared `gateway-message` schema
to `gateway-requests`, `claude-automator-responses`, and `weather-svc-results`).

## Operational notes

- **No Dockerfile, no `docker build`/`docker run`** — `gcloud run deploy --source` builds and
  deploys directly via Cloud Build's Java buildpack.
- **Redeploys**: re-running Part C, step 1 updates the running revision; steps 2-5 are idempotent
  and safe to skip once already done (the push subscription's endpoint doesn't change across
  redeploys of the same Cloud Run service).
- **Smoke-testing before claude-automator's redeploy is safe to do against `weather-svc-results`
  directly** (subscribe a temporary consumer to it, the same pattern claude-automator's own
  `smoke-test-weather.mts` uses for the *opposite* side) — see
  [`../../../docs/PIPELINE.md`](../../../docs/PIPELINE.md)'s WEATHER notes for status. Publishing
  through the full chain (a real Slack `/get-weather` command) only works once claude-automator
  accepts `WEATHER`/`FETCHED` — the "Deployment order" gate this doc opens with.
