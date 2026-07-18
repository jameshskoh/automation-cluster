---
status: ACCEPTED
---

# Cross-process correlation via disk files

## Context

Within a single Claude Code session, `SessionStart` and `Stop` each run as their own `ts-node`
invocation — separate OS processes with no shared memory. The outer loop (`run.ts`) that spawns
the `claude` CLI process is a third, longer-lived process. None of these three can hand off state
to each other directly (no shared variables, no IPC channel), so `claude-automator` uses the
local filesystem as the handoff mechanism.

## Decision

Three files carry state across process boundaries:

| File | Written by | Content | Read by | Purpose |
|---|---|---|---|---|
| `PID_FILE` | Outer loop, at spawn time | PID of the `claude` child process | `Stop` hook | Lets `Stop` find and kill the Claude Code process it doesn't otherwise have a handle to. |
| `UUID_PATH` | `SessionStart` hook, after a message is pulled | The inbound message's `request_id` | `Stop` hook (then deleted) | Lets `Stop` stamp the outbound `ANSWERED` envelope with the `request_id` it's answering. |
| `ACK_ID_PATH` | `SessionStart` hook, after a message is pulled | The Pub/Sub `ackId` of the inbound message | `Stop` hook (then deleted) | Lets `Stop` acknowledge the original inbound message so it's removed from the input subscription. |

`UUID_PATH` and `ACK_ID_PATH` are deleted by `Stop` as soon as they're read, so a stale value
can't leak into the next session.

## Kill mechanism

Process termination rides on this same scheme: `Stop` reads `PID_FILE` and sends `SIGTERM` to
that PID. This happens **unconditionally** — after `Stop` attempts to publish the answer and
acknowledge the input message, regardless of whether that attempt succeeded — so a failed
publish/ack never leaves the Claude Code process running past the end of its turn.

## Known gap

This is file-based correlation, not an in-process mechanism — see the repo-root
`docs/backlog.md` for the standing item to replace it. Revisit if/when `claude-automator` needs to
handle concurrent sessions or becomes less single-process-per-request in shape.
