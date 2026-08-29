#!/usr/bin/env bash
set -euo pipefail

# Registers the shared GatewayMessage Pub/Sub schema and attaches it (JSON-encoded) to every
# topic that carries it.
#
# The schema is Protobuf-defined (schemas/gateway_message.proto) but messages are still published
# as plain JSON on the wire — attaching the schema with --message-encoding=json makes Pub/Sub
# validate each publish's JSON body against the Protobuf message structure without requiring
# binary encoding on either side. See docs/arch/messaging.md for the envelope definition.
#
# This is repo-shared infrastructure (unlike topics, which are owned per-publisher), so it lives
# at the repo root rather than under any one service's scripts/.
#
# Run order: gateway-svc/scripts/provision-pubsub.sh, claude-automator/scripts/provision-pubsub.sh,
# and weather-svc/scripts/provision-pubsub.sh must all run FIRST — a schema can only be attached to
# a topic that already exists.
#
# Usage: GCP_PROJECT_ID=my-project ./scripts/provision-pubsub-schema.sh

: "${GCP_PROJECT_ID:?GCP_PROJECT_ID must be set}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SCHEMA_NAME="gateway-message"
SCHEMA_DEFINITION_FILE="$REPO_ROOT/schemas/gateway_message.proto"

TOPICS_USING_SCHEMA=(
  "gateway-requests"
  "claude-automator-responses"
  "weather-svc-results"
)

echo "Project: ${GCP_PROJECT_ID}"

if gcloud pubsub schemas describe "$SCHEMA_NAME" --project "$GCP_PROJECT_ID" >/dev/null 2>&1; then
  echo "Schema already exists, skipping: $SCHEMA_NAME"
else
  echo "Creating schema: $SCHEMA_NAME"
  gcloud pubsub schemas create "$SCHEMA_NAME" \
    --project "$GCP_PROJECT_ID" \
    --type=protocol-buffer \
    --definition-file="$SCHEMA_DEFINITION_FILE"
fi

for topic in "${TOPICS_USING_SCHEMA[@]}"; do
  current_schema=$(gcloud pubsub topics describe "$topic" --project "$GCP_PROJECT_ID" \
    --format="value(schemaSettings.schema)" 2>/dev/null || true)
  if [[ -n "$current_schema" ]]; then
    echo "Topic already has a schema attached, skipping: $topic"
  else
    echo "Attaching schema to topic: $topic"
    gcloud pubsub topics update "$topic" \
      --project "$GCP_PROJECT_ID" \
      --schema="$SCHEMA_NAME" \
      --message-encoding=json
  fi
done

echo "Done."
