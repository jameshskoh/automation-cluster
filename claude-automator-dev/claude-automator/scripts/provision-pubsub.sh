#!/usr/bin/env bash
set -euo pipefail

# Provisions claude-automator's own Pub/Sub infrastructure for the QA use case:
#   - creates claude-automator-responses (its outbound topic + DLQ)
#   - creates claude-automator's subscription against gateway-requests
#
# Run order: gateway-svc/scripts/provision-pubsub.sh must run FIRST — this script's subscription targets
# gateway-requests, a topic that script owns and creates.
#
# See docs/arch/topics-and-provisioning.md for the ownership convention and idempotency caveat
# (safe to re-run for additions; subscription filters are immutable), and docs/use-cases/qa.md for
# the use case.
#
# Usage: GCP_PROJECT_ID=my-project ./scripts/provision-pubsub.sh

: "${GCP_PROJECT_ID:?GCP_PROJECT_ID must be set}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
# shellcheck source=../../../scripts/pubsub-lib.sh
source "$REPO_ROOT/scripts/pubsub-lib.sh"

# Owned by claude-automator: outbound topic for the QA use case's ANSWERED stage.
CLAUDE_AUTOMATOR_RESPONSES_TOPIC="claude-automator-responses"
CLAUDE_AUTOMATOR_RESPONSES_DLQ_TOPIC="claude-automator-responses-gateway-sub-dlq"

# Owned by the gateway (created by its own provisioning script): inbound topic for the QA use
# case's ASKED stage. claude-automator only creates its own subscription against it here.
GATEWAY_REQUESTS_TOPIC="gateway-requests"
GATEWAY_REQUESTS_DLQ_TOPIC="gateway-requests-claude-automator-sub-dlq"
CLAUDE_AUTOMATOR_SUBSCRIPTION="claude-automator-gateway-requests-sub"
CLAUDE_AUTOMATOR_FILTER='attributes.use_case="QA" AND attributes.stage="ASKED"'

echo "Project: ${GCP_PROJECT_ID}"

# --- Infrastructure this service (claude-automator) owns ---
create_topic_if_missing "$CLAUDE_AUTOMATOR_RESPONSES_TOPIC"
create_topic_if_missing "$CLAUDE_AUTOMATOR_RESPONSES_DLQ_TOPIC"

# --- This service's own subscription against a topic owned by another service ---
create_subscription_if_missing "$CLAUDE_AUTOMATOR_SUBSCRIPTION" "$GATEWAY_REQUESTS_TOPIC" "$CLAUDE_AUTOMATOR_FILTER" "$GATEWAY_REQUESTS_DLQ_TOPIC"

echo "Done."
echo
echo "Set these when deploying claude-automator:"
echo "  PUBSUB_TOPIC_ID=${CLAUDE_AUTOMATOR_RESPONSES_TOPIC}"
echo "  PUBSUB_SUBSCRIPTION_ID=${CLAUDE_AUTOMATOR_SUBSCRIPTION}"
