---
name: sdlc-run
description: One-shot, self-contained agentic SDLC pipeline for url-shortener-service — analyzes the code, decomposes the request into a gated dependency graph, executes it with retries/rollback and human approval, validates, and summarizes, all in a single invocation. Use when the user wants a feature added, a bug fixed, or code refactored end to end in one command, without stepping through the individual sdlc-decompose/orchestrate/validate/summarize skills.
---

# sdlc-run

Turn a plain-English request about `url-shortener-service` into a reviewed, tested,
committed (with your approval) change — in one command, one file, no dependency on
invoking any other skill. This is the easy path for manual testing and everyday use;
if you want to inspect or edit the graph between phases, use the granular
`sdlc-decompose` / `sdlc-orchestrate` / `sdlc-validate` / `sdlc-summarize` skills
instead — they do the same work broken into steps.

The request text is whatever was passed as arguments to this skill. If none was
given, ask the user what they want before doing anything else.

## Prerequisites

This skill commits changes (with your approval, see Phase 2) so it needs the repo to
be a git repository with a clean working tree before it starts (`git status --short`
empty). If the tree isn't clean, stop and say what's uncommitted rather than mixing
your changes with the user's.

---

## Phase 1 — analyze and decompose

**Hard rule: never classify the request or write a graph node from its wording
alone.** Always investigate the actual code first.

1. Slugify the request (kebab-case, ~4-6 words) and create
   `orchestrator/runs/<slug>/`.
2. Investigate: read `README.md` and the relevant parts of
   `url-shortener-service/src/main/java/com/agentic/urlshortener/` for what this
   request would touch — grep for relevant class/method names, read the files, check
   what existing tests already cover that area. Use an `Explore` agent when the
   surface area is uncertain; read/grep directly yourself for an already-obvious,
   narrow target. Record what you actually found (files examined, existing test
   coverage, call sites and data flow for anything pre-existing).
3. Classify from that evidence:
   - **brownfield** — the investigation found existing code/behavior this changes.
   - **greenfield** — nothing overlaps; wholly new surface area.
   - **ambiguous** — even after reading the code, you can't write acceptance
     criteria you're confident in.
4. **If ambiguous: stop and ask.** Ask the user your specific open questions before
   drafting any graph node. Record the Q&A as a resolved assumption once answered —
   this is decision lineage and it must be written into the graph file, not left
   only in chat.
5. Decompose into the smallest set of independently-implementable, independently-
   verifiable subtasks. For each: a title, a concrete checkable acceptance criterion
   (a passing test, a specific behavior — "write the code" is never sufficient on
   its own; every implementation node's criterion must include tests), its
   dependencies (if none, it can run in parallel with other independent nodes), its
   executing agent type (`Explore` for investigation-only, `general-purpose` for
   implementation), and a `gate` assigned by checking it against
   `orchestrator/POLICY.md`: `human-approval` for anything matching a listed
   high-impact category (auth/security, schema/migrations, deletions, dependency/
   version changes, prod config), `policy-check` for baseline-checkable items, `auto`
   otherwise.
6. Write `orchestrator/runs/<slug>/graph.yaml`:

   ```yaml
   run:
     slug: <slug>
     request: "<verbatim original request>"
     mode: greenfield | brownfield | ambiguous
     created_at: <ISO8601>
   analysis:
     summary: >
       <what the investigation actually found>
     files_examined: [<path>, ...]
     existing_tests: [<path>, ...]
     impacted: [{module/class: <what>, why: <how this touches it>}]   # brownfield only
   ambiguities: []
   assumptions: []      # question/answer pairs once resolved
   nodes:
     - id: A
       title: <short title>
       description: <what this node does>
       acceptance_criteria: <specific, checkable>
       agent_type: Explore | general-purpose
       gate: auto | policy-check | human-approval
       depends_on: []
       status: pending
   ```

7. Report the slug, mode and why, and the node list with gates, before touching any
   code.

---

## Phase 2 — orchestrate (execute the graph)

Create `orchestrator/runs/<slug>/orchestration-log.md` and
`orchestrator/runs/<slug>/metrics.json` if they don't exist yet; record the run's
start time.

Repeat until every node is `complete`, `blocked` with no path forward, or a
safe-stop is triggered:

1. **Compute ready nodes** — `pending` nodes whose `depends_on` are all `complete`.
   None ready and none pending → the graph is done.
2. **Dispatch**: more than one ready node at once → spawn one `Agent` call per node
   **in parallel, in a single message** (`Explore` or `general-purpose` per the
   node's `agent_type`), each with a self-contained prompt (the agent has no memory
   of this conversation): the node's description, acceptance criterion, and relevant
   file paths from `analysis`.
   - `gate: human-approval` → stop and ask the user to approve the node's
     description and blast radius *before* dispatching it. Ask one at a time if
     more than one such node is ready; don't batch approvals silently.
   - `gate: policy-check` → verify against `orchestrator/POLICY.md`'s baseline
     items before dispatch; a failed check is treated as a failed execution (below).
3. **Commit — always ask first.** Once a node's agent finishes, review what changed
   (`git status --short`, `git diff`), show the user a short diff summary and the
   proposed message (`<node id>: <node title>`), and commit only after explicit
   approval. This applies to every node regardless of its `gate` — the gate governs
   whether *executing* the node needed a pause, not whether the resulting commit
   does. Never combine two nodes' changes into one commit.
4. **Exit gate**: before marking a node `complete`, spot-check its acceptance
   criterion directly (run the specific test it names, or a targeted compile/build
   check) — don't accept the agent's own say-so.
5. **On failure** (exit gate fails, or the agent couldn't complete the node):
   retry up to 2 times total, each retry's prompt including the previous failure so
   it isn't repeated blindly. Still failing → try one documented fallback (a
   narrower version of the same acceptance criterion) if one exists, gated the same
   way. Still no success → propose `git revert` of the node's commit(s) (ask first,
   same as any commit), mark the node and everything depending on it `blocked` with
   the reason.
6. **Safe-stop**: any `blocked` node → finish the current dispatch wave, then stop
   starting new work. Report what completed and what's blocked and why, and ask the
   user to choose: retry with new guidance, skip and continue with what's left, or
   abort. Never keep going silently past a blocked node.
7. **Dynamic re-planning**: if a node's actual result invalidates an assumption from
   `analysis`, or reveals a node is unnecessary or something new is required, edit
   `graph.yaml` directly (add/remove/modify nodes) and log why in
   `orchestration-log.md` — don't push through a plan you now know is wrong.

Log every event (gate asked, dispatch, commit approved/declined, exit-gate result,
retry, rollback, re-plan, node complete, safe-stop) as one line in
`orchestration-log.md`: `<ISO8601> | <node id or "run"> | <event> | <detail>`.

When the graph finishes (or permanently safe-stops), write
`orchestrator/runs/<slug>/metrics.json` from what actually happened — never
placeholders:

```json
{
  "nodes_total": <n>, "nodes_complete": <n>,
  "success_rate": <nodes_complete/nodes_total>,
  "retry_count": <n>, "rollback_count": <n>,
  "mttr_seconds": [<per-incident seconds from failure to recovery>],
  "run_started_at": "<ISO8601>", "run_finished_at": "<ISO8601>",
  "e2e_latency_seconds": <n>
}
```

If the run was aborted at a safe-stop, skip to Phase 4 and say so plainly — don't run
Phase 3 as if the graph completed normally.

---

## Phase 3 — validate

1. Run `mvn -pl url-shortener-service -am -q test`; capture pass/fail, and on
   failure quote the actual assertion, not just "tests failed".
2. For every `complete` node, independently re-check its acceptance criterion — a
   fresh check, not trusting the node's own self-report from Phase 2.
3. Write the risk/trade-off assessment: risks (what could break that isn't covered
   by what ran, anything `POLICY.md`-relevant that wasn't gated but arguably should
   have been), trade-offs (what was deliberately narrowed or skipped, pulled from
   any Phase 2 re-planning entries), and what's explicitly untested.
4. Write `orchestrator/runs/<slug>/validation-report.md` with a test-suite section,
   a per-node acceptance-criteria checklist, risks, trade-offs, untested items, and
   a plain PASS/FAIL verdict. On FAIL, list exactly which criteria failed.

---

## Phase 4 — summarize

Read `graph.yaml`, `orchestration-log.md`, `metrics.json`, and
`validation-report.md` for this run and write
`orchestrator/runs/<slug>/summary.md`:

```markdown
# Engineering summary — <slug>

## Request
<verbatim>

## Plan and rationale
<mode + why, from analysis; why the graph was split the way it was>

## Decision lineage
<ambiguities raised and how resolved; any re-planning that happened and why>

## Artifacts produced
<files created/changed; commit count>

## Validation
<verdict + acceptance-criteria table, from validation-report.md>

## Risks and trade-offs
<from validation-report.md>

## Assumptions
<anything taken as given>

## Limitations
<what this run deliberately left out of scope>

## Reliability metrics
<success rate, retry count, rollback count, MTTR, e2e latency, from metrics.json>
```

Be honest about failures, rollbacks, and scope left out — this document has to be
defensible under review, not a highlight reel.

Report the summary's location and a 2-3 sentence spoken outcome to the user.
