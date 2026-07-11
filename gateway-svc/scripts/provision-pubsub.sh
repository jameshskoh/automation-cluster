#!/usr/bin/env bash
set -euo pipefail

# Provisions the gateway's own Pub/Sub infrastructure for the QA use case:
#   - creates gateway-requests (the gateway's outbound topic + DLQ)
#   - creates the gateway's subscription against claude-automator-responses
#
# Run order: claude-automator-dev/claude-automator/scripts/provision-pubsub.sh must run FIRST —
# this script's subscription targets claude-automator-responses, a topic that script owns and
# creates.
#
# See docs/arch/topics-and-provisioning.md for the ownership convention and idempotency caveat
# (safe to re-run for additions; subscription filters are immutable), and docs/use-cases/qa.md for
# the use case.
#
# Usage: GCP_PROJECT_ID=my-project ./scripts/provision-pubsub.sh

: "${GCP_PROJECT_ID:?GCP_PROJECT_ID must be set}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../../scripts/pubsub-lib.sh
source "$REPO_ROOT/scripts/pubsub-lib.sh"

# Owned by the gateway: outbound topic for the QA use case's ASKED stage, plus the DLQ topic that
# backstops claude-automator's subscription on it (the DLQ sits on the gateway's topic side, so the
# gateway provisions it; claude-automator's script wires its subscription to reference it).
GATEWAY_REQUESTS_TOPIC="gateway-requests"
GATEWAY_REQUESTS_DLQ_TOPIC="gateway-requests-claude-automator-sub-dlq"

# Owned by claude-automator (created by its own provisioning script): inbound topic for the QA
# use case's ANSWERED stage. The gateway only creates its own subscription against it here.
CLAUDE_AUTOMATOR_RESPONSES_TOPIC="claude-automator-responses"
CLAUDE_AUTOMATOR_RESPONSES_DLQ_TOPIC="claude-automator-responses-gateway-sub-dlq"
GATEWAY_SUBSCRIPTION="gateway-claude-automator-responses-sub"
GATEWAY_FILTER='attributes.use_case="QA" AND attributes.stage="ANSWERED"'

echo "Project: ${GCP_PROJECT_ID}"

# --- Infrastructure this service (the gateway) owns ---
create_topic_if_missing "$GATEWAY_REQUESTS_TOPIC"
create_topic_if_missing "$GATEWAY_REQUESTS_DLQ_TOPIC"

# --- This service's own subscription against a topic owned by another service ---
create_subscription_if_missing "$GATEWAY_SUBSCRIPTION" "$CLAUDE_AUTOMATOR_RESPONSES_TOPIC" "$GATEWAY_FILTER" "$CLAUDE_AUTOMATOR_RESPONSES_DLQ_TOPIC"

echo "Done."
echo
echo "Set these when deploying gateway-svc (the gateway):"
echo "  GATEWAY_REQUESTS_PUBSUB_TOPIC_ID=${GATEWAY_REQUESTS_TOPIC}"
echo "  CLAUDE_AUTOMATOR_RESPONSES_PUBSUB_SUBSCRIPTION_ID=${GATEWAY_SUBSCRIPTION}"
