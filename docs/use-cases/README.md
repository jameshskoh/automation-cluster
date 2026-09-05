# Use cases

Each use case describes one chain of asynchronous processing. A chain originates one of two equally
valid ways — see "Two ways a use case originates" in `../architecture.md` — and the required
contents below are the same regardless of which. A use case is the unit that `use_case` (in the
shared message envelope, see `../architecture.md`) identifies.

## Required contents of a use-case doc

Each `docs/use-cases/<use-case-name>.md` file must contain:

1. **Use case name** — matches the `use_case` value carried in every message belonging to this
   chain.
2. **Timeout** — the value the gateway uses to fail this use case's requests if no terminal
   response arrives in time (see `../architecture.md`). Still required for a
   scheduled-task-originated use case, but state what it means there: with no caller connection to
   fail, it bounds how long the chain is expected to take (a log/metric signal) rather than
   triggering a failure response.
3. **Sequence diagram** — mentions the gateway and each service (function) involved, drawn as if
   they call each other directly. Pub/Sub topics/subscriptions are deliberately omitted from the
   diagram for readability.
4. **Stage table** — placed directly below the sequence diagram, to restore the detail the
   diagram simplified away:

   | Publisher | Subscriber | use_case | stage |
   |---|---|---|---|
   | gateway | xxxsvc | `example` | `requested` |
   | xxxsvc | gateway | `example` | `completed` |

   Each row corresponds to one message hop in the sequence diagram, so a developer can map any
   arrow in the diagram to the actual publisher, subscriber, and `use_case`+`stage` values that
   identify that message on the wire.

5. **Origination and terminal delivery** — which of the two origination patterns this use case
   uses, and how its terminal message is delivered (a registered callback back to the caller, or
   gateway-initiated unsolicited delivery to a configured destination). Both are per-use-case
   choices with different failure and duplicate-delivery properties, so a reader should not have to
   infer them from the diagram. See "Two ways a use case originates" and "Gateway-initiated
   (unsolicited) delivery" in `../architecture.md`.

## Template

```markdown
# Use case: <name>

- `use_case` value: `<name>`
- Timeout: <duration>

## Sequence diagram

\`\`\`mermaid
sequenceDiagram
    participant Gateway
    participant XxxSvc
    Gateway->>XxxSvc: request
    XxxSvc->>Gateway: response
\`\`\`

## Stages

| Publisher | Subscriber | use_case | stage |
|---|---|---|---|
| gateway | xxxsvc | `<name>` | `requested` |
| xxxsvc | gateway | `<name>` | `completed` |
```
