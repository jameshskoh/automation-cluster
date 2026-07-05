#!/usr/bin/env bash
# Shared gcloud Pub/Sub provisioning helpers, sourced by each service's scripts/provision-pubsub.sh.
# See docs/arch/topics-and-provisioning.md for the per-service ownership convention this supports
# and the idempotency caveat (safe to re-run for additions; subscription filters are immutable, so
# editing one in place has no effect).

: "${MAX_DELIVERY_ATTEMPTS:=5}"

create_topic_if_missing() {
  local topic="$1"
  if gcloud pubsub topics describe "$topic" --project "$GCP_PROJECT_ID" >/dev/null 2>&1; then
    echo "Topic already exists, skipping: $topic"
  else
    echo "Creating topic: $topic"
    gcloud pubsub topics create "$topic" --project "$GCP_PROJECT_ID"
  fi
}

# Grants the Pub/Sub service agent permission to publish to a dead-letter topic and to
# ack/nack on the source subscription — both are required for dead-lettering to work.
grant_dlq_permissions() {
  local dlq_topic="$1"
  local subscription="$2"
  local project_number
  project_number=$(gcloud projects describe "$GCP_PROJECT_ID" --format="value(projectNumber)")
  local pubsub_sa="service-${project_number}@gcp-sa-pubsub.iam.gserviceaccount.com"

  gcloud pubsub topics add-iam-policy-binding "$dlq_topic" \
    --project "$GCP_PROJECT_ID" \
    --member="serviceAccount:${pubsub_sa}" \
    --role="roles/pubsub.publisher" >/dev/null

  gcloud pubsub subscriptions add-iam-policy-binding "$subscription" \
    --project "$GCP_PROJECT_ID" \
    --member="serviceAccount:${pubsub_sa}" \
    --role="roles/pubsub.subscriber" >/dev/null
}

create_subscription_if_missing() {
  local subscription="$1"
  local topic="$2"
  local filter="$3"
  local dlq_topic="$4"

  if gcloud pubsub subscriptions describe "$subscription" --project "$GCP_PROJECT_ID" >/dev/null 2>&1; then
    echo "Subscription already exists, skipping: $subscription"
    return
  fi

  echo "Creating subscription: $subscription (topic=$topic, filter=$filter)"
  gcloud pubsub subscriptions create "$subscription" \
    --project "$GCP_PROJECT_ID" \
    --topic "$topic" \
    --message-filter "$filter" \
    --dead-letter-topic "$dlq_topic" \
    --max-delivery-attempts "$MAX_DELIVERY_ATTEMPTS"

  grant_dlq_permissions "$dlq_topic" "$subscription"
}
