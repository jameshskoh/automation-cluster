---
status: ACCEPTED
---

# claude-automator: happy path

How a single request flows through `claude-automator` end to end, on the happy path (no empty
polls exhausting `POLL_COUNT`, no validation failures, no publish/ack errors). See
[`nothing-to-do.md`](nothing-to-do.md) for what happens when polling finds nothing.

## Sequence diagram

```mermaid
sequenceDiagram
    participant OuterLoop as Outer loop (run.ts)
    participant ClaudeCode as Claude Code (CLI process)
    participant SessionStart as SessionStart hook
    participant Stop as Stop hook
    participant Disk as Local disk (PID/UUID/ack-id files)
    participant InTopic as Input Pub/Sub
    participant OutTopic as Output Pub/Sub
    participant Metrics as OTLP metrics endpoint

    OuterLoop->>ClaudeCode: spawn `claude` with fixed task prompt
    OuterLoop->>Disk: write PID_FILE

    ClaudeCode->>SessionStart: SessionStart hook fires

    loop until a non-empty message arrives (max POLL_COUNT attempts)
        SessionStart->>InTopic: pull (maxMessages=1)
        InTopic-->>SessionStart: ASKED message (request_id, question) or none
    end

    SessionStart->>Disk: write UUID_PATH (request_id), ACK_ID_PATH (ackId)
    SessionStart->>Metrics: record claude_hook_completed_total{hook_event=SessionStart, outcome=success}
    SessionStart-->>ClaudeCode: additionalContext = question (injected into session)

    ClaudeCode->>ClaudeCode: work the question, write answer to chat
    Note right of ClaudeCode: no further tool calls -> agent loop ends

    ClaudeCode->>Stop: Stop hook fires (last_assistant_message = answer)
    Stop->>Disk: read + delete UUID_PATH, ACK_ID_PATH
    Stop->>OutTopic: publish ANSWERED message (request_id, metadata=answer)
    Stop->>InTopic: acknowledge(ackId)
    Stop->>Metrics: record claude_hook_completed_total{hook_event=Stop, outcome=success}
    Stop->>Disk: read PID_FILE
    Stop->>ClaudeCode: SIGTERM

    ClaudeCode-->>OuterLoop: process exit
    Note over OuterLoop: while(true) repeats from spawn
```

## See also

The design decisions behind specific steps above are recorded separately in `../arch/`:

- [`../arch/messaging.md`](../arch/messaging.md) — the Pub/Sub transport as a whole, and why the
  input side pulls instead of listening for pushed messages.
- [`../arch/disk-correlation.md`](../arch/disk-correlation.md) — why request/ack/process identity
  is handed off via disk files, and what each of the three files carries.
- [`../arch/metrics.md`](../arch/metrics.md) — what gets emitted to the OTLP endpoint and at
  which point in this flow.
