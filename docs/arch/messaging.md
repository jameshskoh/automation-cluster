# Messaging: envelope, schema enforcement, schema evolution

The shared message contract every service publishes and consumes. See
[`../architecture.md`](../architecture.md) for the system model this fits into, and
[`topics-and-provisioning.md`](topics-and-provisioning.md) for how these messages are carried on
Pub/Sub topics/subscriptions.

## Message format

Every message in the system shares a single envelope:

| Field | Purpose |
|---|---|
| `use_case` | Identifies which use case (chain of processing) this message belongs to. |
| `stage` | Identifies which stage of the use case this message represents. |
| `request_id` | UUID, originated and remembered by the gateway; used to match a response back to the request that started it. |
| `payload` | The primary data being processed. |
| `metadata` | Secondary data for "extra purpose" — e.g. LLM instructions, trace context, etc. |

`use_case` + `stage` together uniquely identify what a message is and where it belongs in a use
case's flow. Both values are carried in **two places**:

1. **Pub/Sub message attributes** — used by subscriptions to filter which messages a consumer
   receives natively, without deserializing the body.
2. **The message body fields** — kept for logging/debugging and for any consumer that reads
   multiple use cases off a single subscription and needs the values in code without touching
   Pub/Sub-specific attribute APIs.

## Schema enforcement

The message envelope is defined as a **Protobuf schema** and registered as a **Pub/Sub schema**
on every topic. Messages that don't comply with the schema are rejected — but note precisely
*when* and *how*:

- **Schema validation happens at publish time**, synchronously, inside the publishing service's
  own process. A non-compliant message never reaches the topic at all — the publish call itself
  fails.
- This means a schema violation surfaces as a **processing failure of whatever message the
  publisher was handling when it tried to publish**. Concretely: function F consumes message A,
  does its side effect, and then tries to publish message B — B fails schema validation. That
  publish failure is F's own failure to finish processing A. F's Pub/Sub client nacks A, A is
  redelivered, retries eventually exhaust, and **A lands in A's own subscription's DLQ** (not a
  new or separate DLQ for B, since B never existed as a published message). Replaying A from the
  DLQ re-runs F and reproduces the same schema error, making it directly debuggable — safe to do
  repeatedly thanks to the idempotency rule (see [`../architecture.md`](../architecture.md),
  "Functions").
- **Exception — the gateway.** When the gateway itself is the publisher (i.e., sending the first
  message of a use case), there is no upstream topic/subscription for a failed publish to
  dead-letter into. The gateway must catch this synchronously and immediately fail the request
  through the same failure-response + registry-cleanup path used for timeouts.
- **claude-automator has no exception**: a schema-violation-triggered processing failure follows
  the same nack → retry → DLQ path as any other function.

## Schema evolution

Because the schema is Protobuf, backward compatibility is enforced by convention plus Protobuf's
native optional-field semantics:

- Existing fields must never be removed or renumbered.
- New fields may be added, and must be optional with a sensible default so that services running
  an older version of the schema continue to work unmodified.
- A CI/code-review check should confirm a new schema revision only adds optional fields —
  tracked as a roadmap item in `../to-do.md` (not yet implemented).
