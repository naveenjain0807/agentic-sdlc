---
name: sdlc-decompose
description: Turn a feature/bug/refactor request into an evidence-based task graph for the url-shortener-service target system. Analyzes the actual codebase before classifying the request as greenfield, brownfield, or ambiguous, then writes a dependency graph with acceptance criteria and gates. Use when the user wants a requirement decomposed before any code is touched, or as the first step of /sdlc-run.
---

# sdlc-decompose

Convert a plain-English request about `url-shortener-service` into
`orchestrator/runs/<slug>/graph.yaml` — a dependency graph of subtasks, grounded in
what the codebase actually contains, not in what the request's wording implies.

The request text is whatever was passed as arguments to this skill. If none was
given, ask the user what they want before doing anything else.

## Hard rule: analyze before you classify

Never decide greenfield/brownfield/ambiguous, and never write a single graph node,
before you have actually looked at the code. Guessing from the request's wording is
the one thing this skill must not do.

1. Read `README.md` and skim `url-shortener-service/src/main/java/com/agentic/urlshortener/`
   layout (packages: `config`, `domain`, `repository`, `service`, `exception`, `web`)
   if you don't already have it loaded.
2. For the specific request, do a real, targeted investigation: grep for relevant
   class/method names, read the files that would plausibly be touched, check what
   existing tests already cover that area (`url-shortener-service/src/test/java/...`).
   Use the `Explore` agent for this when the surface area is uncertain; for a narrow,
   already-obvious target, just Read/Grep directly yourself.
3. Only after that investigation, write the analysis findings down (see schema
   below) and draw your conclusions from them.

## Workflow

### 1. Set up the run

Slugify the request (kebab-case, ~4-6 words) and create
`orchestrator/runs/<slug>/`. If a directory for a very similar slug already exists
from today, append `-2`, `-3`, etc. rather than overwriting.

### 2. Investigate (see Hard Rule above)

Record what you actually found:
- Files examined and what's in them relevant to this request.
- Existing tests that already cover this area, and what they currently assert.
- For anything that looks like it touches existing behavior: the call sites, the
  data flow in and out, and any other module that would be affected.

### 3. Classify the mode from the evidence

- **brownfield** — the investigation found existing code/behavior this request
  changes.
- **greenfield** — the investigation found nothing that overlaps; this is wholly new
  surface area (new endpoint, new class, new module) with no existing implementation
  to reconcile with.
- **ambiguous** — even after reading the code, you cannot write acceptance criteria
  you're confident in (the request is underspecified, contradicts itself, or could
  reasonably mean two different things). This is a conclusion from investigation +
  request text together, not just from the text alone.

### 4. If ambiguous: stop and ask

Do not draft graph nodes yet. Ask the user your specific open questions (via a direct
question, offering concrete options where you can). Once answered, record the
question and answer as a resolved assumption — this is decision lineage, and it must
survive in the graph file, not just in chat. Then continue to step 5.

### 5. Decompose into a dependency graph

Break the (now well-understood) problem into the smallest set of subtasks that can
each be independently implemented and verified. For each node, write:
- a one-line title,
- a concrete, checkable acceptance criterion (something `sdlc-validate` can actually
  verify — a passing test, a specific behavior, a file existing),
- which existing subtasks it depends on (if any) — independent nodes have no
  dependency and can run in parallel later,
- which agent type should execute it (`Explore` for investigation-only nodes,
  `general-purpose` for implementation nodes),
- a `gate`, assigned by checking the node's description against
  `orchestrator/POLICY.md`: `human-approval` if it matches a listed high-impact
  category, `policy-check` if it should be checked against the baseline items but
  doesn't need a human pause, `auto` otherwise.

Every implementation node's acceptance criterion must include tests for what it
adds or changes — "write the code" is never a complete acceptance criterion on its
own.

### 6. Write graph.yaml

```yaml
run:
  slug: <slug>
  request: "<verbatim original request>"
  mode: greenfield | brownfield | ambiguous
  created_at: <ISO8601 timestamp>
analysis:
  summary: >
    <what step 2's investigation actually found, in prose>
  files_examined:
    - <path>
  existing_tests:
    - <path>
  impacted:              # brownfield only; omit or leave empty for greenfield
    - module/class: <what>
      why: <how this request touches it>
ambiguities: []           # the open questions raised, even once resolved
assumptions: []           # question/answer pairs once the user has answered
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

### 7. Report back

Print the slug, the mode and why, and a short bulleted list of the nodes with their
gates, so the user (or `sdlc-orchestrate`, if chained via `sdlc-run`) knows what's
about to happen before any code changes.
