# claude-automator: nothing to do

What happens when `SessionStart` polls the input subscription and finds no message for
`POLL_COUNT` rounds in a row — the counterpart to [`happy-path.md`](happy-path.md).

## Sequence diagram

```mermaid
sequenceDiagram
    participant OuterLoop as Outer loop (run.ts)
    participant ClaudeCode as Claude Code (CLI process)
    participant SessionStart as SessionStart hook
    participant Stop as Stop hook
    participant Disk as Local disk (PID/UUID/ack-id files)
    participant InTopic as Input Pub/Sub
    participant Metrics as OTLP metrics endpoint

    OuterLoop->>ClaudeCode: spawn `claude` with fixed task prompt
    OuterLoop->>Disk: write PID_FILE

    ClaudeCode->>SessionStart: SessionStart hook fires

    loop POLL_COUNT attempts
        SessionStart->>InTopic: pull (maxMessages=1)
        InTopic-->>SessionStart: none
    end
    Note right of SessionStart: UUID_PATH/ACK_ID_PATH are never written this round

    SessionStart->>Metrics: record claude_hook_completed_total{hook_event=SessionStart, outcome=success}
    SessionStart-->>ClaudeCode: additionalContext = "Polled POLL_COUNT times. Nothing to do now."

    ClaudeCode->>ClaudeCode: nothing to work on, says so, agent loop ends

    ClaudeCode->>Stop: Stop hook fires
    Stop->>Disk: read UUID_PATH -> not found
    Note right of Stop: requestId is null -> abort before publish/ack
    Stop->>Metrics: record claude_hook_completed_total{hook_event=Stop, outcome=failure, reason=uuid_missing}
    Stop->>Disk: read PID_FILE
    Stop->>ClaudeCode: SIGTERM

    ClaudeCode-->>OuterLoop: process exit
    Note over OuterLoop: while(true) repeats from spawn
```

## Timing

Sleeps only happen *between* attempts, not after the last one (`pubsub-client.ts`: `if (i <
POLL_COUNT - 1) await sleep(POLL_INTERVAL_MS)`), so a fully-empty poll blocks the session for
about:

```
(POLL_COUNT - 1) x POLL_INTERVAL_MS
```

— one interval short of the naive `POLL_COUNT x POLL_INTERVAL_MS`. With the `.env.example`
defaults (`POLL_COUNT=2`, `POLL_INTERVAL_MS=5000`), that's `5s` of sleep, plus each `pull()`
call's own round-trip latency (variable — this is a lower bound).

## Safeguard

`sendMessage()` aborts with `reason=uuid_missing` if `UUID_PATH` is missing — already true here
(see [`../arch/disk-correlation.md`](../arch/disk-correlation.md)) — so an empty poll can't publish
an erroneous answer. Caveat: relies on the prior `Stop` run having deleted the file; a crash
beforehand could leave a stale `ackId` behind.
