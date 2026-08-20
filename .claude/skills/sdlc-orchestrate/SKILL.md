---
name: sdlc-orchestrate
description: Execute a graph.yaml produced by sdlc-decompose against url-shortener-service — non-linear/stateful execution with entry/exit gates, parallel and sequential dispatch, bounded retries, rollback, safe-stop, and dynamic re-planning. Use after sdlc-decompose has produced a graph, or as the second step of /sdlc-run.
---

# sdlc-orchestrate

Walk `orchestrator/runs/<slug>/graph.yaml` to completion, executing each node
through a real `Agent` call, honoring dependencies and gates, and leaving a
timestamped audit trail plus reliability metrics behind.

Takes a path to a `graph.yaml` (or a slug under `orchestrator/runs/`) as its
argument. If given a raw task description instead of a graph, run `sdlc-decompose`
on it first, then continue with the graph it produces.

## Prerequisites

This skill commits per node and can roll back via `git revert`, so it needs the repo
to be a git repository with a clean working tree before it starts (`git status
--short` should be empty, aside from `orchestrator/runs/<slug>/graph.yaml` itself).
If the tree isn't clean, stop and tell the user what's uncommitted rather than
mixing your changes with theirs.

**Every commit and every revert requires the user's explicit approval before it
runs** — show the diff and the proposed message, wait for a yes, then commit. This
applies regardless of a node's `auto`/`policy-check`/`human-approval` gate; that gate
controls whether *running* the node needs approval, not whether the resulting commit
does.

Create/confirm `orchestrator/runs/<slug>/orchestration-log.md` and
`orchestrator/runs/<slug>/metrics.json` exist (create empty ones if not) before
starting. Record the run's start time for the end-to-end latency metric.

## Main loop

Repeat until every node is `complete`, `blocked` with no path forward, or a
safe-stop is triggered:

1. **Compute ready nodes** — any node still `pending` whose `depends_on` are all
   `complete`. If none are ready and none are `pending`, the graph is done.
2. **Dispatch**: if more than one node is ready at once, spawn one `Agent` call per
   node **in parallel, in a single message** (this is the actual parallel-path
   requirement — don't dispatch them one at a time across separate messages).
   - `Explore` agent type for investigation-only nodes, `general-purpose` for
     implementation nodes, matching what `sdlc-decompose` assigned.
   - Each agent's prompt must be self-contained: the node's description, its
     acceptance criterion, and the relevant file paths from the graph's `analysis`
     section — the agent has no memory of this conversation.
   - Before dispatching a node with `gate: human-approval`, stop and ask the user to
     approve it, showing the node's description and what it will touch. Do not batch
     multiple approval requests silently — ask, wait, then proceed. A node with
     `gate: policy-check` is instead checked against `orchestrator/POLICY.md`'s
     baseline items before dispatch; if a check fails, treat it like a failed
     execution (see retry/rollback below) rather than silently proceeding.
3. **Per-node commit — always ask first**: once a node's agent finishes, review what
   changed (`git status --short`, `git diff`) and show the user a short summary of
   the diff plus the proposed commit message (`<node id>: <node title>`). Only run
   `git commit` after they explicitly approve it — never commit unilaterally, even
   for an `auto`-gated node; the `auto`/`human-approval`/`policy-check` gate controls
   whether *executing* the node needs approval, it does not exempt the resulting
   commit from approval. Never let two nodes' changes land in the same commit.
4. **Exit gate**: before marking the node `complete`, spot-check its acceptance
   criterion — run the specific test it names, or a quick compile/build check. Don't
   accept "the agent said it's done" as sufficient.
5. **On failure** (exit gate fails, or the agent reports it couldn't complete the
   node):
   - Retry up to 2 times total, each retry's prompt including the previous attempt's
     failure output so it isn't repeating the same mistake.
   - If still failing after retries: check for a documented fallback (a narrower
     version of the same acceptance criterion). If a viable fallback exists, try it
     once, gated the same way as the original node.
   - If no fallback succeeds: propose `git revert` of the node's commit(s) if any
     landed, and ask the user to approve the revert the same way as any other commit
     before running it. Mark the node `blocked` in `graph.yaml` with the reason, mark
     every node that `depends_on` it `blocked` too (don't silently skip them or let
     them run against a not-yet-reverted dependency).
6. **Safe-stop**: if any node is `blocked`, stop dispatching new work once the
   current wave finishes. Report the graph's state (what completed, what's blocked
   and why) and ask the user to choose: retry blocked node(s) with new guidance,
   skip them and continue with what's left, or abort the run. Do not keep executing
   downstream-unaffected nodes silently past a blocked node without saying so first —
   surface it, even if you'd then continue.
7. **Dynamic re-planning**: if a node's actual result reveals the plan was wrong —
   an assumption from `graph.yaml`'s `analysis` didn't hold, a node turns out
   unnecessary, or new work is clearly required — edit `graph.yaml` directly: add,
   remove, or modify nodes, and append why to `orchestration-log.md`. Don't push
   through a plan you now know is wrong just because it's what was written down
   first.

## Logging and metrics

After every event (gate passed/asked, dispatch, commit, exit-gate result, retry,
rollback, re-plan edit, node complete, safe-stop), append one line to
`orchestration-log.md`:

```
<ISO8601 timestamp> | <node id or "run"> | <event> | <one-line detail>
```

When the graph finishes (or safe-stops permanently), write
`orchestrator/runs/<slug>/metrics.json`:

```json
{
  "nodes_total": <n>,
  "nodes_complete": <n>,
  "success_rate": <nodes_complete / nodes_total>,
  "retry_count": <n>,
  "rollback_count": <n>,
  "mttr_seconds": [<seconds from failure detected to recovery, per incident>],
  "run_started_at": "<ISO8601>",
  "run_finished_at": "<ISO8601>",
  "e2e_latency_seconds": <n>
}
```

These numbers must come from what actually happened in this run — never
placeholders.

## Report back

Summarize what completed, what (if anything) was blocked/rolled back/re-planned,
and point to `orchestration-log.md` and `metrics.json` for detail.
