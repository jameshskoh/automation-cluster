# Use cases

Each use case describes one chain of asynchronous processing, starting and ending at the gateway.
A use case is the unit that `use_case` (in the shared message envelope, see `../architecture.md`)
identifies.

## Required contents of a use-case doc

Each `docs/use-cases/<use-case-name>.md` file must contain:

1. **Use case name** — matches the `use_case` value carried in every message belonging to this
   chain.
2. **Timeout** — the value the gateway uses to fail this use case's requests if no terminal
   response arrives in time (see `../architecture.md`).
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
