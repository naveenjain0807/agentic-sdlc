# Testing approach, limitations, and trade-offs

## Testing approach

**The three scenarios are the regression suite.** There's no unit-testable "engine"
separate from the skill instructions themselves — the skills *are* the orchestrator,
interpreted by Claude Code at run time. So the way to catch a regression in the
orchestration behavior (a skill file edit that quietly breaks gating, or retry logic,
or the graph schema) is the same way you'd catch one in the behavior it's supposed to
produce: re-run `orchestrator/scenarios/greenfield.md`, `brownfield.md`, and
`ambiguous.md` as live invocations against a clean working tree, and diff the new
run's `graph.yaml`/`metrics.json`/`validation-report.md` shape against the captured
baseline. A skill change that breaks how a gate is enforced, or how a node is
retried, shows up as a shape or behavior difference in one of the three runs.

**`sdlc-validate` is tested by construction, not separately.** Its job is to
re-derive a verdict from the real `mvn test` run plus a fresh check of each
acceptance criterion — so any run of the pipeline that reaches the validate step is
implicitly testing that `sdlc-validate` correctly parses pass/fail and correctly
re-checks criteria, because a wrong verdict would be caught by the human reviewing
`summary.md` against what the code actually does.

**The target system's own test suite is the ground truth for behavior.**
`url-shortener-service`'s `Base62CodecTest` and `UrlShortenerApiIntegrationTest`
define what "correct" means for the code the orchestrator touches. `mvn -pl
url-shortener-service -am test` must pass after every orchestrated change — that's
the exit gate for every implementation node, not just a final check.

## Limitations

- **Non-determinism across runs.** Two `sdlc-run` invocations of the same request
  can produce differently-shaped graphs or slightly different implementations,
  because decomposition and implementation are real reasoning, not a fixed script.
  The scenario baselines capture *a* correct run, not *the* only correct run.
- **No cross-wave transactional rollback.** Rollback is per-node (`git revert` of
  that node's commit), not a single atomic transaction across the whole graph. If
  node C depends on B which depends on A, and C fails unrecoverably, A and B's
  commits stand; only C (and anything depending on it) is marked `blocked`. This is
  a deliberate trade-off (see below), not an oversight, but it means "the graph
  failed" doesn't always mean "the repo is back to where it started."
- **Single-stack assumptions baked into the skill prompts.** The skills know about
  Maven, `mvn test`, the package layout under
  `com.agentic.urlshortener`, and this repo's conventions specifically. Pointing
  them at a different stack or a second module would need the skill instructions
  updated, not just a different target path.
- **Human-in-the-loop is required, by design, at every gate — and at every commit.**
  `human-approval` nodes and safe-stops block progress until a person responds, and
  independently of a node's gate, every git commit and revert also waits for
  explicit approval before it runs. There's no fully unattended mode; a run with
  many nodes means many individual approval prompts, not just one at the start.
- **Context-window ceiling on graph size.** A very large request decomposed into
  dozens of nodes, each dispatched with a full self-contained prompt, will eventually
  strain the coordinating session's context. The orchestrator is sized for
  feature/bugfix/refactor-scale requests against one service, not a multi-service
  program of work.
- **No persistent process.** Nothing runs when a skill isn't actively invoked — if a
  `human-approval` gate is pending and the session ends, resuming requires re-running
  `sdlc-orchestrate` against the same `graph.yaml`, not a background process picking
  back up on its own.

## Trade-offs

- **Skills + structured artifacts vs. a standalone LLM-calling orchestration
  service.** Chosen: skills. Far simpler, zero extra infrastructure or API key,
  reuses an already-robust agent runtime instead of re-implementing one. Given up:
  the system can't run headless/unattended, and can't be deployed independently of
  a Claude Code session.
- **Git-commit-based rollback vs. a transactional workflow engine.** Chosen:
  per-node commits. Honest, inspectable with plain `git log`/`git show`, and needs
  no new infrastructure. Given up: rollback granularity is per-node, not
  whole-graph-atomic (see Limitations above).
- **Coarse dependency-graph parallelism vs. a fine-grained scheduler.** Chosen:
  recompute "ready" nodes each pass through a simple loop. Easy to reason about and
  to audit from the log. Given up: efficiency on graphs with many small,
  fine-grained interdependencies, where a real scheduler would pack work more
  tightly.
- **File-based state vs. an in-memory/database-backed run state.** Chosen:
  `graph.yaml` plus markdown/JSON artifacts on disk, committed to git. Given up:
  no concurrent-run safety (two `sdlc-orchestrate` invocations against the same slug
  at once would race) — acceptable because runs are meant to be interactive and
  single-threaded, one human driving one run at a time.
