# Run artifacts

Each invocation of the `sdlc-*` skills creates one directory here, named by a
slugified version of the request:

```
<slug>/
  graph.yaml               dependency graph + live node status (the audit/state artifact)
  orchestration-log.md     timestamped event log: gates, dispatch, commits, retries, rollbacks, re-plans
  metrics.json             success rate, retry/rollback counts, MTTR, end-to-end latency
  validation-report.md     test results, acceptance-criteria check, risk/trade-off assessment
  summary.md               Final Engineering Summary for the run
```

These are generated output, not hand-authored — don't edit them directly except
through the skills that own them (`sdlc-decompose` owns `graph.yaml`,
`sdlc-orchestrate` appends to it and to `orchestration-log.md`/`metrics.json`,
`sdlc-validate` owns `validation-report.md`, `sdlc-summarize` owns `summary.md`).

They're committed to the repo (not gitignored) because `graph.yaml` and
`orchestration-log.md` together *are* the audit trail — deleting them would erase
the record the orchestration model is supposed to provide.
