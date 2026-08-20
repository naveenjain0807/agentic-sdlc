# Orchestrate: running the SDLC pipeline

A step-by-step runbook for turning a plain-English request into a reviewed, tested,
committed change to `url-shortener-service`, using the `sdlc-*` skills. Written for
someone who has never run this before — follow it top to bottom and you shouldn't
hit a surprise. For the *why* behind the design, see
[`ARCHITECTURE.md`](ARCHITECTURE.md); for the *what counts as high-risk*, see
[`POLICY.md`](POLICY.md).

## 0. Prerequisites (check these before you start)

1. **Clone and enter the repo**, then confirm it's a git repo with your git user
   configured — every node lands as a commit attributed to you.
2. **Working tree must be clean.**

   ```bash
   git status --short
   ```

   Empty output or stop. `sdlc-orchestrate` commits per node and needs a clean
   starting point to know what it changed; if you have in-progress work, commit or
   stash it (`git stash -u`) first.
3. **Confirm the build actually works, before you touch anything.**

   ```bash
   mvn -pl url-shortener-service -am -q test
   ```

   This should finish green. If it fails to even *parse* (`[FATAL] Non-parseable
   POM ...`), don't proceed into a run — fix that first as its own prerequisite
   commit (see §6, this has happened before in this exact repo).
4. **Run Claude Code from the repo root** (`claude`), so `.claude/skills/` and
   `.claude/commands/` are picked up. If typing a skill name by itself doesn't
   trigger it, use the explicit `/sdlc-run`, `/sdlc-decompose`, etc. slash commands
   shipped under `.claude/commands/`.

## 1. Kick off a run

**Easiest — one shot:**

```
/sdlc-run "add a QR code endpoint for short links"
```

This chains all four phases below with no extra steps from you beyond answering
questions and approving gates/commits as they come up.

**Granular — inspect/edit between phases:**

```
/sdlc-decompose "..."           # produces graph.yaml — read/edit it
/sdlc-orchestrate orchestrator/runs/<slug>/graph.yaml
/sdlc-validate <slug>
/sdlc-summarize <slug>
```

Use the granular path when you want to review or hand-edit the task graph before
any code is touched.

## 2. What happens, phase by phase

### Phase 1 — decompose
The request is **never** classified from its wording alone — the skill reads the
actual code first (relevant controllers/services/tests under
`url-shortener-service/src/main/java/com/agentic/urlshortener/`), then classifies
the request as **greenfield** (nothing existing overlaps), **brownfield** (touches
existing behavior), or **ambiguous**. Ambiguous stops and asks you directly — your
answers get written into `graph.yaml` as resolved assumptions, not left only in
chat. The output is `orchestrator/runs/<slug>/graph.yaml`: a dependency graph of
nodes, each with a title, a concrete checkable acceptance criterion (always
including a test), dependencies, an executing agent type, and a `gate`
(`auto` / `policy-check` / `human-approval`, assigned by checking `POLICY.md`).

### Phase 2 — orchestrate
The main loop: compute which nodes are ready (dependencies satisfied), dispatch
them — in parallel if more than one is ready at once, each as a self-contained
`Agent` call with no memory of anything outside its own prompt. Before a node with
`gate: human-approval` runs, you're asked to approve its description and blast
radius. After a node finishes, its acceptance criterion is independently
re-checked (never just the agent's own say-so) — then, **regardless of gate**, you
are shown the diff and asked to approve the commit before it lands. A failed exit
gate retries up to twice, then tries one narrower fallback if one exists, then
proposes a `git revert` and marks the node (and anything depending on it)
`blocked`. Any `blocked` node triggers a **safe-stop**: the run finishes the
current wave, reports what completed/what's blocked/why, and asks you to retry,
skip, or abort — it never keeps going silently past a blocker.

### Phase 3 — validate
Runs the real test suite (`mvn -pl url-shortener-service -am -q test`), re-checks
every `complete` node's acceptance criterion fresh, and writes
`validation-report.md`: a test-suite section, a per-node checklist, risks,
trade-offs, untested items, and a plain PASS/FAIL verdict.

### Phase 4 — summarize
Rolls `graph.yaml` + `orchestration-log.md` + `metrics.json` +
`validation-report.md` into one `summary.md` — plan/rationale, decision lineage,
artifacts produced, validation verdict, risks/trade-offs, assumptions,
limitations, reliability metrics. This is the document to hand to a reviewer.

## 3. What you'll be asked to approve, and when

| When | What you see |
|---|---|
| Request is ambiguous | A specific clarifying question before any graph node is written |
| A node has `gate: human-approval` | The node's description + blast radius, before it's dispatched |
| **Every** node finishes (any gate) | The diff + proposed commit message — approval required before commit |
| A node is unrecoverably failing | A proposal to `git revert` its commit(s) — approval required |
| Any node ends up `blocked` | A safe-stop: retry with new guidance / skip and continue / abort |

Approving one commit or gate does not pre-approve the next one — expect to be
asked at each of these points, every run.

## 4. Reading the artifacts afterward

Everything a run produces lives in `orchestrator/runs/<slug>/`:

| File | Contents |
|---|---|
| `graph.yaml` | The plan: analysis, nodes, gates, status, resolved assumptions |
| `orchestration-log.md` | One line per event — dispatch, approval, commit, retry, rollback, re-plan |
| `metrics.json` | Success rate, retry/rollback counts, MTTR, end-to-end latency — computed from real events |
| `validation-report.md` | Test results + acceptance-criteria checklist + risk assessment |
| `summary.md` | The final, reviewable write-up |

`git log` on the commits made during the run is the audit trail; `git revert` on
any of them is the rollback mechanism.

## 5. Worked example (real run in this repo)

`orchestrator/runs/health-check-ping-endpoint/` is a completed run for the request
*"add a health-check ping endpoint that returns the current server time."*

- **Decompose** classified it **greenfield** — no existing controller returned
  server time (Actuator's `/actuator/health` reports UP/DOWN status, not a
  timestamp, and editing Actuator config would itself have been a
  `human-approval` production-config change, so a new endpoint was used instead).
  One node, gate `auto` (new endpoint, no auth/schema/deps/prod-config touched).
- **Orchestrate** dispatched the single node, which added `PingController.java`,
  `dto/PingResponse.java`, and a `MockMvc` test asserting `GET /api/v1/ping`
  returns 200 with a `status` field and a `timestamp` within a few seconds of
  `Instant.now()`.
- Before the node's exit gate could even run, `mvn` failed outright with
  `[FATAL] Non-parseable POM` — a **pre-existing, unrelated** defect: root
  [`pom.xml`](../pom.xml) had a literal `--` inside an XML comment, which is
  illegal XML and made Maven refuse to parse the reactor POM at all. This was
  fixed as its own prerequisite commit (`6f980ea`, *not* bundled into the
  feature commit) after asking for approval, precisely because it was outside
  the node's declared scope.
- With the build unblocked, the exit gate was independently re-run and passed
  (`UrlShortenerApiIntegrationTest` 12/12, `Base62CodecTest` 5/5, including the
  new ping test), the diff was shown, and the feature commit (`3acb9ab`) landed
  only after explicit approval.

Read `orchestration-log.md` in that directory for the full event-by-event trace.

## 6. Troubleshooting / known gotchas

- **`mvn` fails with `[FATAL] Non-parseable POM ... in comment after two dashes
  (--)`.** This is illegal XML — a literal `--` inside an `<!-- ... -->` comment
  somewhere in a `pom.xml`. It already happened once in this repo's root
  `pom.xml` (fixed in commit `6f980ea`); if you see it again, someone
  reintroduced a `--` inside a comment. Grep for it (`grep -n -- '--' pom.xml`),
  fix the comment text, and land the fix as its **own small commit** before
  resuming your actual node — don't bundle an unrelated build fix into a
  feature's commit.
- **Build is already broken before you start.** Every node's exit gate depends on
  `mvn -pl url-shortener-service -am -q test` actually running. If step 0.3 above
  fails, stop and fix the build first (with approval, as a standalone commit) —
  don't start a run against a repo that can't build.
- **Working tree isn't clean.** `sdlc-run`/`sdlc-orchestrate` will refuse to
  start. `git status --short`, then commit or `git stash -u` what's there.
- **A node keeps failing after retries.** Expect a proposed `git revert` and a
  safe-stop, not silent progress. Answer with retry (with new guidance), skip, or
  abort — whichever fits.
- **`pom.xml`, `application.yml`, `docker-compose.yml`, an `@Entity`, or anything
  auth-related is in scope.** These are `human-approval` categories in
  [`POLICY.md`](POLICY.md) regardless of how small the change looks — expect an
  explicit approval prompt, every time, no exceptions.

## 7. More detail

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — orchestration model, control-flow diagram, key design decisions
- [`POLICY.md`](POLICY.md) — gate levels and the categories that always require `human-approval`
- [`TESTING.md`](TESTING.md) — testing approach, limitations, trade-offs
