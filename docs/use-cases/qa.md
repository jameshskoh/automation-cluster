# Use case: QA

- `use_case` value: `QA`
- Timeout: 300000 ms (5 minutes) — matches `qa.async.timeout-millis` in `ai-svc`'s
  `application.yaml` today; see `../to-do.md` for generalizing this into a declared per-use-case
  property.

## Sequence diagram

```mermaid
sequenceDiagram
    participant Gateway
    participant ClaudeAutomator as claude-automator
    Gateway->>ClaudeAutomator: question
    ClaudeAutomator->>Gateway: answer
```

## Stages

| Publisher | Subscriber | use_case | stage |
|---|---|---|---|
| gateway | claude-automator | `QA` | `ASKED` |
| claude-automator | gateway | `QA` | `ANSWERED` |

## Topics and subscriptions

Each service provisions only the infrastructure it owns (see
`../arch/topics-and-provisioning.md`): its own
outbound topic + DLQ, and its own subscription against whichever topic it consumes. Run
`ai-svc/scripts/provision-pubsub.sh` before `claude-automator/scripts/provision-pubsub.sh` (each
service's subscription targets the other service's topic, so the topic must exist first — see
each script's header comment for the exact order dependency).

| Topic (owner) | Subscription (owner) | Filter | Dead-letter topic | Provisioned by |
|---|---|---|---|---|
| `gateway-requests` (gateway) | `claude-automator-gateway-requests-sub` (claude-automator) | `use_case="QA" AND stage="ASKED"` | `gateway-requests-claude-automator-sub-dlq` | `../../ai-svc/scripts/provision-pubsub.sh` (topic) + `../../claude-automator/scripts/provision-pubsub.sh` (subscription) |
| `claude-automator-responses` (claude-automator) | `gateway-claude-automator-responses-sub` (gateway) | `use_case="QA" AND stage="ANSWERED"` | `claude-automator-responses-gateway-sub-dlq` | `../../claude-automator/scripts/provision-pubsub.sh` (topic) + `../../ai-svc/scripts/provision-pubsub.sh` (subscription) |

## Known gaps for this use case

See `../to-do.md` for full detail. Notably: the message envelope on the wire today is
`{uuid, question}` / `{uuid, answer}`, not yet the unified `{use_case, stage, request_id, payload,
metadata}` envelope — so the `use_case`/`stage` attribute filters above don't yet match anything
until the publishers are updated to send them.
