---
name: sdlc-validate
description: Validate a completed sdlc-orchestrate run against url-shortener-service — runs the real test suite, checks each graph node's acceptance criterion, and writes a risk/trade-off assessment. Use after sdlc-orchestrate finishes, or as the third step of /sdlc-run.
---

# sdlc-validate

Confirm that a run's changes actually work and are safe to hand back to a human,
writing `orchestrator/runs/<slug>/validation-report.md`.

Takes a slug (or path to a `graph.yaml`) as its argument. If none is given, use the
most recently modified directory under `orchestrator/runs/`.

## Workflow

### 1. Run the real test suite

```bash
mvn -pl url-shortener-service -am -q test
```

Capture pass/fail and, on failure, which tests failed and why (don't just report
"tests failed" — quote the assertion).

### 2. Check acceptance criteria

For every node in `graph.yaml` marked `complete`, independently verify its
acceptance criterion is actually true right now — re-run the specific test it names,
or check the specific behavior/file it claims. Do not trust the node's own
self-report from `orchestrate` without re-checking; that's the point of a separate
validation pass.

### 3. Risk and trade-off assessment

This is not just pass/fail. For what actually changed in this run, write:
- **Risks**: what could break that isn't covered by the tests that ran (edge cases,
  concurrency, data migration risk, anything `orchestrator/POLICY.md` flags that
  wasn't given a `human-approval` gate but arguably should have been).
- **Trade-offs**: what was deliberately not done, and why (scope narrowed on
  purpose, a simpler approach chosen over a more general one, etc.) — pull this from
  `orchestration-log.md`'s re-planning entries if any exist.
- **What's untested**: call out gaps explicitly rather than letting silence imply
  full coverage.

### 4. Write validation-report.md

```markdown
# Validation report — <slug>

## Test suite
<pass/fail, failure detail if any>

## Acceptance criteria
- [x|f] <node id>: <criterion> — <what you checked>

## Risks
- ...

## Trade-offs
- ...

## Untested
- ...

## Verdict
PASS | FAIL — <one line>
```

On FAIL, list exactly which acceptance criteria failed so a follow-up
`sdlc-orchestrate` run can target just those nodes instead of redoing everything.

### 5. Report back

State the verdict plainly, and point to the full report for detail.
