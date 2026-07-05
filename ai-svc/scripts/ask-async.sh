#!/usr/bin/env bash
set -euo pipefail

# Calls the long-polling async Q&A endpoint.
# Usage: ./scripts/ask-async.sh "your question here"

HOST="127.0.0.1:8081"

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 \"<question>\"" >&2
  exit 1
fi

question="$1"
payload=$(printf '{"question":%s}' "$(printf '%s' "$question" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')")

# --max-time slightly exceeds the server-side 5-minute DeferredResult timeout
# so curl doesn't give up before the server responds (e.g. with 504).
curl -sS \
  --max-time 310 \
  -X POST "http://${HOST}/qa/async" \
  -H "Content-Type: application/json" \
  -d "$payload"
echo
