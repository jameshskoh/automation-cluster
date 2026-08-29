#!/usr/bin/env bash
set -euo pipefail

# Provisions claude-automator's own Pub/Sub infra for the QA and WEATHER use cases:
#   - claude-automator-responses: outbound topic + DLQ, shared by both use cases
#   - subscription against gateway-requests (QA)
#   - subscription against weather-svc-results (WEATHER)
#
# Run order: gateway-svc's and weather-svc's provision-pubsub.sh must both run FIRST — the QA and
# WEATHER subscriptions target gateway-requests and weather-svc-results, topics those scripts own
# and create. docs/use-cases/weather.md's "Provisioning run order" (repo root) has the full
# cross-service two-pass sequence this fits into.
#
# Ownership convention + idempotency caveat (safe to re-run for additions; subscription filters are
# immutable): docs/arch/topics-and-provisioning.md. The use cases: docs/use-cases/{qa,weather}.md
# (repo root).
#
# Usage: GCP_PROJECT_ID=my-project ./scripts/provision-pubsub.sh

: "${GCP_PROJECT_ID:?GCP_PROJECT_ID must be set}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
# shellcheck source=../../../scripts/pubsub-lib.sh
source "$REPO_ROOT/scripts/pubsub-lib.sh"

# Owned by claude-automator: outbound topic for the ANSWERED stage, shared by both use cases.
CLAUDE_AUTOMATOR_RESPONSES_TOPIC="claude-automator-responses"
CLAUDE_AUTOMATOR_RESPONSES_DLQ_TOPIC="claude-automator-responses-gateway-sub-dlq"

# Owned by the gateway (its own script creates it): inbound topic for QA/ASKED. We only create our
# subscription against it here.
GATEWAY_REQUESTS_TOPIC="gateway-requests"
GATEWAY_REQUESTS_DLQ_TOPIC="gateway-requests-claude-automator-sub-dlq"
CLAUDE_AUTOMATOR_SUBSCRIPTION="claude-automator-gateway-requests-sub"
CLAUDE_AUTOMATOR_FILTER='attributes.use_case="QA" AND attributes.stage="ASKED"'

# Owned by weather-svc (its own script creates it): inbound topic for WEATHER/FETCHED. We only
# create our subscription against it here.
WEATHER_SVC_RESULTS_TOPIC="weather-svc-results"
WEATHER_SVC_RESULTS_DLQ_TOPIC="weather-svc-results-claude-automator-sub-dlq"
CLAUDE_AUTOMATOR_WEATHER_SUBSCRIPTION="claude-automator-weather-svc-results-sub"
CLAUDE_AUTOMATOR_WEATHER_FILTER='attributes.use_case="WEATHER" AND attributes.stage="FETCHED"'

echo "Project: ${GCP_PROJECT_ID}"

# --- Infrastructure this service (claude-automator) owns ---
create_topic_if_missing "$CLAUDE_AUTOMATOR_RESPONSES_TOPIC"
create_topic_if_missing "$CLAUDE_AUTOMATOR_RESPONSES_DLQ_TOPIC"

# --- This service's own subscriptions against topics owned by other services ---
create_subscription_if_missing "$CLAUDE_AUTOMATOR_SUBSCRIPTION" "$GATEWAY_REQUESTS_TOPIC" "$CLAUDE_AUTOMATOR_FILTER" "$GATEWAY_REQUESTS_DLQ_TOPIC"
create_subscription_if_missing "$CLAUDE_AUTOMATOR_WEATHER_SUBSCRIPTION" "$WEATHER_SVC_RESULTS_TOPIC" "$CLAUDE_AUTOMATOR_WEATHER_FILTER" "$WEATHER_SVC_RESULTS_DLQ_TOPIC"

echo "Done."
echo
echo "Set these when deploying claude-automator:"
echo "  PUBSUB_TOPIC_ID=${CLAUDE_AUTOMATOR_RESPONSES_TOPIC}"
echo "  PUBSUB_SUBSCRIPTION_ID=${CLAUDE_AUTOMATOR_SUBSCRIPTION}"
echo "  PUBSUB_WEATHER_SUBSCRIPTION_ID=${CLAUDE_AUTOMATOR_WEATHER_SUBSCRIPTION}"
