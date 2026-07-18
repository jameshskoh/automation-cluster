---
status: ACCEPTED
---

# Metrics

## What's emitted

Both hooks report to a single OTLP counter, `claude_hook_completed_total`, exported to
`OTLP_METRICS_URL` via `recordHookCompleted()` (`.claude/hooks/utils/metrics.ts`). Each call
force-flushes and shuts down its own `MeterProvider` immediately rather than batching on an
interval, since each hook is a short-lived CLI invocation. A metrics-export failure is caught and
logged; it never changes the hook's own exit code.

The counter carries three labels: `hook_event` (`SessionStart` | `Stop`), `outcome` (`success` |
`failure`), and `reason` (populated only on failure, empty string on success).

## Emission points

| Hook | Outcome | `reason` | Emitted when |
|---|---|---|---|
| `SessionStart` | `success` | *(empty)* | `processUsefulMessage()` resolves without throwing — covers both "found a message and handed it off" and "polled `POLL_COUNT` times, found nothing." |
| `SessionStart` | `failure` | `poll_error` | `processUsefulMessage()` throws (e.g. the Pub/Sub `pull` call fails). |
| `Stop` | `success` | *(empty)* | `sendMessage()` returns `{ ok: true }` — the answer was published **and** the original input message was acknowledged. |
| `Stop` | `failure` | `uuid_missing` | `sendMessage()` found no `UUID_PATH` on disk, so there's nothing to correlate the answer to; nothing is published. |
| `Stop` | `failure` | `ackid_missing` | The answer was published, but no `ACK_ID_PATH` was found, so the original input message can't be acknowledged and may redeliver. |
| `Stop` | `failure` | `publish_error` | `sendMessage()` throws (e.g. the Pub/Sub `publish` or `acknowledge` call fails). |

See [`../use-cases/happy-path.md`](../use-cases/happy-path.md) for where these emission points
sit in the overall sequence.

The `Stop`/`uuid_missing` row also fires on the benign "nothing to do" case (see
[`../use-cases/nothing-to-do.md`](../use-cases/nothing-to-do.md)), which isn't really a failure —
tracked in the repo-root `docs/backlog.md`, not fixed yet.
