---
name: article-summary-workflow
description: The article-summary use case is the first non-gateway-originated flow, and it forced three system-level changes with knock-on effects
metadata:
  type: project
---

`docs/use-cases/article-summary.md` (`ARTICLE_SUMMARY`) is the first use case in this system that
does **not** originate at the gateway — an `article-svc` scheduled task starts it, and it ends with
the gateway pushing to Slack with no caller connection held open.

**Why:** it forced three system-level changes at once, each of which reopened something previously
settled:
1. A second origination pattern ("scheduled-task-originated") in `docs/architecture.md`.
2. "Gateway-initiated (unsolicited) delivery" as a new gateway capability — keyed on
   `use_case`+`stage`, not `request_id`, with a statically configured destination.
3. `claude-automator` moving from one-deployment-per-use-case to one instance serving a
   per-use-case registry — which set `claude-automator` to `REOPENED` in `docs/PIPELINE.md`
   for both design and implementation.

**How to apply:** when reviewing anything in this workflow, remember that most system-level prose
predates the existence of non-gateway-originated flows and quietly assumes a caller connection
exists. Statements about timeouts, failure delivery, the callback registry, and `request_id`
ownership are the ones most likely to be stale rather than wrong-by-design. See
[[edge-cases-non-gateway-originated-flows]].
