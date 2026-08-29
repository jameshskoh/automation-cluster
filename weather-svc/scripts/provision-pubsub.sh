#!/usr/bin/env bash
set -euo pipefail

# Provisions weather-svc's own Pub/Sub infrastructure for the WEATHER use case:
#   - weather-svc-results: outbound topic (weather-svc owns it; publishes both FETCHED and FAILED)
#   - gateway-requests-weather-svc-sub-dlq: the DLQ backstopping weather-svc's own subscription
#     (DLQ topics are owned by the subscription they backstop, per
#     docs/arch/topics-and-provisioning.md - a deliberate divergence from the QA precedent where
#     the *gateway* created claude-automator's DLQ)
#   - weather-svc-gateway-requests-sub: weather-svc's own subscription against gateway-requests
#     (owned by gateway-svc), filter WEATHER AND REQUESTED
#
# Run order: gateway-svc/scripts/provision-pubsub.sh must run FIRST - this script's subscription
# targets gateway-requests, a topic that script owns and creates. See
# docs/use-cases/weather.md's "Provisioning run order" (repo root) for the full cross-service
# two-pass sequence this fits into, and docs/arch/topics-and-provisioning.md for the ownership
# convention + idempotency caveat (safe to re-run for additions; subscription filters are
# immutable).
#
# weather-svc's subscription is a Pub/Sub *push* subscription (docs/architecture.md, "Trigger"),
# not the pull-only shape the shared create_subscription_if_missing (scripts/pubsub-lib.sh)
# creates, so this script defines its own create_push_subscription_if_missing below rather than
# extending the shared library (out of this task's file-ownership; see
# docs/arch/messaging.md's "Provisioning note").
#
# The push endpoint is only known after weather-svc's first deploy (see docs/deploy/README.md), so
# this script is meant to run TWICE:
#   1. Before the first deploy: creates the topic/DLQ only (WEATHER_SVC_PUSH_ENDPOINT unset ->
#      the subscription step is skipped with instructions printed).
#   2. After the first deploy: re-run with WEATHER_SVC_PUSH_ENDPOINT set to create the push
#      subscription itself. Safe to re-run any number of times after that (idempotent).
#
# Usage:
#   GCP_PROJECT_ID=my-project ./scripts/provision-pubsub.sh
#   GCP_PROJECT_ID=my-project WEATHER_SVC_PUSH_ENDPOINT=https://weather-svc-xxxx.a.run.app \
#     WEATHER_SVC_PUSH_SERVICE_ACCOUNT=weather-svc-push@my-project.iam.gserviceaccount.com \
#     ./scripts/provision-pubsub.sh

: "${GCP_PROJECT_ID:?GCP_PROJECT_ID must be set}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# weather-svc is a short-lived push-triggered function (one open-meteo round-trip per request),
# not a long-running consumer like claude-automator - override the shared library's 600s default
# (sized for claude-automator's full claude CLI session) before sourcing it, so its "${VAR:=...}"
# picks this up instead. Still overridable by the caller's own environment.
: "${ACK_DEADLINE_SECONDS:=60}"
export ACK_DEADLINE_SECONDS

# shellcheck source=../../scripts/pubsub-lib.sh
source "$REPO_ROOT/scripts/pubsub-lib.sh"

# Owned by weather-svc: outbound topic for both FETCHED and FAILED (one topic per publisher; the
# two consumer subscriptions filter by stage).
WEATHER_SVC_RESULTS_TOPIC="weather-svc-results"

# Owned by gateway-svc (its own script creates it): inbound topic for WEATHER/REQUESTED. We only
# create our own subscription against it here.
GATEWAY_REQUESTS_TOPIC="gateway-requests"

# weather-svc's own subscription + the DLQ that backstops it.
WEATHER_SVC_SUBSCRIPTION="weather-svc-gateway-requests-sub"
WEATHER_SVC_SUBSCRIPTION_DLQ="gateway-requests-weather-svc-sub-dlq"
WEATHER_SVC_FILTER='attributes.use_case="WEATHER" AND attributes.stage="REQUESTED"'

# Creates a *push* subscription (gcloud --push-endpoint), unlike pubsub-lib.sh's
# create_subscription_if_missing (pull-only) - see docs/arch/messaging.md, "Provisioning note".
create_push_subscription_if_missing() {
  local subscription="$1"
  local topic="$2"
  local filter="$3"
  local dlq_topic="$4"
  local push_endpoint="$5"
  local push_auth_service_account="${6:-}"

  if gcloud pubsub subscriptions describe "$subscription" --project "$GCP_PROJECT_ID" >/dev/null 2>&1; then
    echo "Subscription already exists, skipping: $subscription"
    return
  fi

  echo "Creating push subscription: $subscription (topic=$topic, filter=$filter, push-endpoint=$push_endpoint)"

  local -a push_auth_args=()
  if [[ -n "$push_auth_service_account" ]]; then
    push_auth_args=(--push-auth-service-account="$push_auth_service_account")
  else
    echo "WARNING: WEATHER_SVC_PUSH_SERVICE_ACCOUNT not set - creating an UNAUTHENTICATED push" \
         "subscription. Only fine if weather-svc's Cloud Run service allows unauthenticated" \
         "invocations; for a real deployment prefer setting it (see docs/deploy/README.md)."
  fi

  gcloud pubsub subscriptions create "$subscription" \
    --project "$GCP_PROJECT_ID" \
    --topic "$topic" \
    --push-endpoint "$push_endpoint" \
    "${push_auth_args[@]}" \
    --message-filter "$filter" \
    --dead-letter-topic "$dlq_topic" \
    --max-delivery-attempts "$MAX_DELIVERY_ATTEMPTS" \
    --ack-deadline "$ACK_DEADLINE_SECONDS"

  grant_dlq_permissions "$dlq_topic" "$subscription"
}

echo "Project: ${GCP_PROJECT_ID}"

# --- Infrastructure this service (weather-svc) owns ---
create_topic_if_missing "$WEATHER_SVC_RESULTS_TOPIC"
create_topic_if_missing "$WEATHER_SVC_SUBSCRIPTION_DLQ"

# --- This service's own subscription against a topic owned by another service (gateway-svc) ---
if [[ -n "${WEATHER_SVC_PUSH_ENDPOINT:-}" ]]; then
  create_push_subscription_if_missing \
    "$WEATHER_SVC_SUBSCRIPTION" "$GATEWAY_REQUESTS_TOPIC" "$WEATHER_SVC_FILTER" \
    "$WEATHER_SVC_SUBSCRIPTION_DLQ" "$WEATHER_SVC_PUSH_ENDPOINT" "${WEATHER_SVC_PUSH_SERVICE_ACCOUNT:-}"
else
  echo "WEATHER_SVC_PUSH_ENDPOINT not set - skipping push subscription creation."
  echo "Deploy weather-svc first (see docs/deploy/README.md), then re-run this script with:"
  echo "  WEATHER_SVC_PUSH_ENDPOINT=<the deployed Cloud Run URL>"
fi

echo "Done."
echo
echo "Set these when deploying weather-svc:"
echo "  WEATHER_RESULTS_TOPIC_ID=${WEATHER_SVC_RESULTS_TOPIC}"
