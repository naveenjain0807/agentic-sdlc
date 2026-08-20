---
name: sdlc-summarize
description: Produce the Final Engineering Summary for a completed sdlc run against url-shortener-service — plan/rationale, artifacts, risks/trade-offs/validation, assumptions, and limitations in one reviewable document. Use after sdlc-validate finishes, or as the last step of /sdlc-run.
---

# sdlc-summarize

Roll up everything a run produced into one reviewable
`orchestrator/runs/<slug>/summary.md`, written for a human reviewer who has not seen
the run happen and doesn't want to read `graph.yaml`, the log, and the validation
report separately.

Takes a slug (or path) as its argument. If none is given, use the most recently
modified directory under `orchestrator/runs/`.

## Workflow

Read `graph.yaml`, `orchestration-log.md`, `metrics.json`, and
`validation-report.md` for this run, then write:

```markdown
# Engineering summary — <slug>

## Request
<verbatim original request>

## Plan and rationale
<the mode classification and why, pulled from graph.yaml's analysis section, plus
a short narrative of the node breakdown and why it was split that way>

## Decision lineage
<any ambiguities raised and how they were resolved (from graph.yaml's
ambiguities/assumptions); any dynamic re-planning that happened mid-run and why
(from orchestration-log.md)>

## Artifacts produced
<files created/changed, one line each; commit count>

## Validation
<pull the verdict and acceptance-criteria table from validation-report.md>

## Risks and trade-offs
<pull from validation-report.md>

## Assumptions
<anything taken as given without explicit confirmation>

## Limitations
<what this run deliberately did not do, and what a follow-up would need to cover>

## Reliability metrics
<success rate, retry count, rollback count, MTTR, e2e latency — from metrics.json>
```

Keep it honest: if something failed, was rolled back, or was left out of scope, say
so plainly rather than smoothing it over — this document is meant to be defensible
under review, not a highlight reel.

## Report back

Print the summary's location and a 2-3 sentence spoken summary of the outcome.
