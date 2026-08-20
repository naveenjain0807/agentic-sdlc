# Orchestration policy

Shared reference for the `sdlc-*` skills. `sdlc-decompose` uses this to assign a
`gate` to every graph node it writes; `sdlc-orchestrate` uses it to enforce that gate
before executing a node. Neither skill hand-rolls its own judgment call about what's
"risky" — both point here so the rule is defined once and applied consistently.

## Gate levels

- `auto` — no pause. Default for anything that doesn't match a category below.
- `policy-check` — orchestrate verifies the node's planned change against the
  baseline checks in this file before running it; no human pause unless a check
  fails.
- `human-approval` — orchestrate stops and asks the user (via a direct question)
  to approve the node's description and blast radius before it executes, regardless
  of task mode (greenfield/brownfield/ambiguous) or how small the change looks.

## Categories that always require `human-approval`

- **Auth / security** — anything touching authentication, authorization, session or
  token handling, CORS, or the `exception`/security-adjacent parts of the request
  pipeline.
- **Schema / migrations** — any new or edited file under `db/migration/`, or any
  entity (`@Entity`) field/table change.
- **Data deletion or destructive operations** — hard deletes, `DROP`/`TRUNCATE`,
  clearing of persisted data, log/history purges. (Soft-delete flows that already
  exist, like `UrlShortenerService.deactivate`, are not destructive by this
  definition — a *new* hard-delete path would be.)
- **Dependency / version changes** — edits to `pom.xml` (new dependency, version
  bump), Java/Spring Boot version changes, base image changes in `Dockerfile`.
- **Production configuration** — changes to `application.yml` defaults that affect
  runtime behavior in a deployed environment (ports, datasource URLs, exposed
  actuator endpoints), or `docker-compose.yml`.

Everything else — new endpoints, new service/domain classes, refactors confined to
existing behavior, tests, documentation — defaults to `auto`.

## Baseline `policy-check` items

Before any node executes, regardless of its gate level:

- No secrets, credentials, or API keys committed (grep the diff for obvious
  patterns before committing).
- No authentication/authorization check is disabled, weakened, or removed as a side
  effect of an unrelated change.
- No query or endpoint is introduced without a bound (pagination limit, max page
  size) if it returns a collection — mirror the existing `MAX_PAGE_SIZE` pattern in
  `UrlController`.
- No new endpoint skips input validation that equivalent existing endpoints perform.

## How gates interact with retries and rollback

A `human-approval` pause happens once per node, before the first execution attempt —
not on every retry. If a node is retried after a failure, the retry re-executes
under the same already-granted approval; if orchestration re-plans and changes what
the node actually does, the changed node is treated as new and re-gated.
