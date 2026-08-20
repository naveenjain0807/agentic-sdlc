# URL Shortener Service

Target system for the **Agentic SDLC Orchestration** take-home. A runnable Spring Boot
service that creates short links, redirects them, and reports click analytics.

* Java 21 · Spring Boot 3.4 · Spring Data JPA
* H2 (in-memory) with **Flyway** migrations — DDL/DML run once, replay-safe
* Swagger UI / OpenAPI 3 for interactive testing
* One-command Docker run

The orchestration layer lives beside this module as Claude Code skills, not a second
Maven module — see [section 8](#8-orchestrator-skills).

---

## 1. Run it

```bash
docker compose up --build
```

First build takes a few minutes (Maven downloads dependencies inside the image).
When it finishes:

| What | URL |
|---|---|
| **Swagger UI** | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| H2 console | <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:urlshortener`, user `sa`, no password) |
| Health | <http://localhost:8080/actuator/health> |
| Metrics (Prometheus) | <http://localhost:8080/actuator/prometheus> |

Stop with `docker compose down`.

Plain Docker works too:

```bash
docker build -t agentic/url-shortener-service:0.1.0 .
docker run --rm -p 8080:8080 agentic/url-shortener-service:0.1.0
```

Or run it locally without Docker:

```bash
mvn -pl url-shortener-service -am spring-boot:run
```

---

## 2. Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/shorten` | Create a short link |
| `GET` | `/{shortCode}` | 302 redirect to the original URL + record a click |
| `GET` | `/api/v1/urls` | List links (paged) |
| `GET` | `/api/v1/urls/{shortCode}` | Link metadata |
| `DELETE` | `/api/v1/urls/{shortCode}` | Soft delete (history preserved) |
| `GET` | `/api/v1/analytics/{shortCode}` | Per-link click analytics |
| `GET` | `/api/v1/analytics` | Service-wide roll-up |

### Try it with curl

```bash
# create
curl -s -X POST http://localhost:8080/api/v1/shorten \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://www.google.com/search?q=spring+boot"}'
```

```json
{
  "shortCode" : "3kQm9f",
  "shortUrl" : "http://localhost:8080/3kQm9f",
  "originalUrl" : "https://www.google.com/search?q=spring+boot",
  "createdAt" : "2026-08-20T18:41:02.117Z",
  "active" : true,
  "expired" : false,
  "totalClicks" : 0,
  "createdBy" : "api"
}
```

```bash
# follow it (-i shows the raw 302, no --location so you see the header)
curl -i http://localhost:8080/3kQm9f

# analytics
curl -s http://localhost:8080/api/v1/analytics/3kQm9f

# service-wide summary
curl -s http://localhost:8080/api/v1/analytics
```

Three links are seeded by migration `V2` so there is something to click immediately:
`/welcome`, `/docs`, `/h2db`.

### Request options

```jsonc
{
  "url": "https://example.com",   // required, absolute http(s)
  "customAlias": "my-link",       // optional vanity code, 3-32 of [A-Za-z0-9_-]
  "ttlSeconds": 3600,             // optional, mutually exclusive with expiresAt
  "expiresAt": "2026-12-31T23:59:59Z",
  "createdBy": "naveen"           // optional audit tag
}
```

Send an `Idempotency-Key` header to make retries safe — the same key always returns
the same link. Without a key, re-posting the same URL (no alias, no expiry) re-uses
the existing link and returns `200` instead of `201`.

### Status codes

| Code | When |
|---|---|
| `201` | New link created |
| `200` | Existing link re-used (dedupe / idempotency replay) |
| `302` | Redirect on `GET /{shortCode}` |
| `400` | Invalid URL, bad expiry, failed validation (`fieldErrors` in the body) |
| `404` | Unknown or deleted code |
| `409` | Custom alias already taken |
| `410` | Link expired |

> Swagger's "Try it out" follows redirects, so the redirect endpoint will show you the
> target page rather than the 302. Use `curl -i` to see the raw `Location` header.

---

## 3. Data model

`short_url`

| Column | Notes |
|---|---|
| `short_code` | unique, base62 |
| `original_url`, `url_hash` | `url_hash` = SHA-256, used for de-duplication |
| `idempotency_key` | optional, from the `Idempotency-Key` header |
| `expires_at` | null = never expires |
| `active` | soft delete flag |
| `total_clicks`, `last_accessed_at` | denormalised counters kept in step on each redirect |

`click_event` — append-only fact table, one row per redirect:
`clicked_at`, `ip_address`, `user_agent`, `referer`, `device_type`, FK to `short_url`
with `ON DELETE CASCADE`.

Analytics (`clicks per day`, `top referers`, `device mix`, `unique visitors`) are
aggregated in Java over a bounded window of recent events, which keeps the SQL
portable to Postgres unchanged.

### Short code generation

A database sequence (`short_code_seq`) is multiplied by a constant coprime with 62^6
and encoded base62. The mapping is a bijection, so codes are collision-free without a
read-before-write, and they do not look sequential — you cannot enumerate other
people's links by incrementing a code. Moving this to a Redis `INCR` later touches one
method (`ShortCodeGenerator#nextRawValue`).

---

## 4. Migrations

`url-shortener-service/src/main/resources/db/migration/`

| Script | Contents |
|---|---|
| `V1__create_core_schema.sql` | sequence, `short_url`, `click_event`, indexes |
| `V2__seed_demo_data.sql` | three demo links |

Flyway records applied versions in `flyway_schema_history`, so each script runs
**exactly once** per database. On top of that, every statement is written to be
replay-safe by hand: DDL uses `CREATE ... IF NOT EXISTS`, and the seed DML uses
`MERGE INTO ... KEY (short_code)` so re-running updates in place instead of failing on
a duplicate key.

To add a change, drop a new `V3__*.sql` beside them — never edit an applied script.

Check what ran: <http://localhost:8080/actuator/flyway>

---

## 5. Configuration

Everything is overridable by environment variable.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `APP_BASE_URL` | `http://localhost:8080` | Base used when rendering `shortUrl` |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:urlshortener;DB_CLOSE_DELAY=-1` | Datasource |
| `APP_DEFAULT_TTL_SECONDS` | `0` (never expires) | Default link lifetime |
| `APP_RECENT_CLICK_LIMIT` | `20` | Clicks returned by the analytics endpoint |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0` | JVM flags |

**Persisting data across restarts.** The database is in-memory by default, so a
restart resets it. To keep it, uncomment the volume block in `docker-compose.yml` and
set:

```yaml
SPRING_DATASOURCE_URL: jdbc:h2:file:/data/urlshortener;DB_CLOSE_ON_EXIT=FALSE
```

**Moving to Postgres** is a dependency swap plus a datasource URL — the Flyway scripts
are standard SQL apart from `MERGE ... KEY`, which becomes
`INSERT ... ON CONFLICT DO UPDATE`.

---

## 6. Tests

```bash
mvn -pl url-shortener-service -am test
```

* `Base62CodecTest` — codec round-trips, padding, and that 5 000 sequential inputs
  scramble to 5 000 distinct six-character codes.
* `UrlShortenerApiIntegrationTest` — full MockMvc coverage: shorten → redirect →
  analytics, de-duplication, idempotency keys, custom aliases and their conflicts,
  soft delete, validation errors, seeded links, OpenAPI document, actuator health.

The Docker build runs with `-DskipTests` so image builds stay fast; run the suite
locally or in CI.

---

## 7. Layout

```
.
├── Dockerfile                  multi-stage: maven build -> temurin JRE runtime
├── docker-compose.yml
├── pom.xml                     reactor root
├── .claude/skills/             the sdlc-* orchestrator skills (see section 8)
├── orchestrator/                POLICY.md, ARCHITECTURE.md, TESTING.md, scenarios/, runs/
└── url-shortener-service/
    ├── pom.xml
    └── src/main/
        ├── java/com/agentic/urlshortener/
        │   ├── config/         AppProperties, OpenApiConfig
        │   ├── domain/         ShortUrl, ClickEvent
        │   ├── repository/     Spring Data repositories
        │   ├── service/        Base62Codec, ShortCodeGenerator, UrlShortenerService,
        │   │                   ClickTrackingService, AnalyticsService, DeviceTypes
        │   ├── exception/      typed domain exceptions
        │   └── web/            controllers, mapper, DTOs, error handling
        └── resources/
            ├── application.yml
            └── db/migration/   V1, V2
```

---

## 8. Orchestrator skills

The `url-shortener-service` above is the **target system**. Alongside it, this repo
also carries an agentic SDLC orchestration layer — five parameterized [Claude
Code](https://claude.com/claude-code) skills under `.claude/skills/` that turn a
plain-English request into an evidence-based, gated, auditable change to the target
system. No separate service, no LLM API key — the orchestration engine is Claude
Code itself, invoked through these skills.

**Prerequisite**: run `claude` from this repo's root, and keep the working tree
clean before invoking `sdlc-orchestrate` or `sdlc-run` (they commit per step and
need git for rollback/audit).

**Invocation**: skills under `.claude/skills/` aren't slash commands by themselves —
Claude Code auto-invokes them when your request matches a skill's description, or
you can name one directly ("use the sdlc-run skill to add..."). For a guaranteed
`/sdlc-run "..."` slash command, this repo also ships thin wrappers under
`.claude/commands/` that point at each skill; use those if typing the skill name
alone doesn't trigger it.

| Skill | Purpose | Example |
|---|---|---|
| `/sdlc-run` | End-to-end: decompose → orchestrate → validate → summarize | `/sdlc-run "add a QR code endpoint for short links"` |
| `/sdlc-decompose` | Analyze the code, classify greenfield/brownfield/ambiguous, build a dependency graph | `/sdlc-decompose "add rate limiting to POST /api/v1/shorten"` |
| `/sdlc-orchestrate` | Execute a graph: gates, parallel/sequential dispatch, retries, rollback, re-planning | `/sdlc-orchestrate orchestrator/runs/<slug>/graph.yaml` |
| `/sdlc-validate` | Run the real test suite and check acceptance criteria + risk/trade-offs | `/sdlc-validate <slug>` |
| `/sdlc-summarize` | Final Engineering Summary for a run | `/sdlc-summarize <slug>` |

For most requests, `/sdlc-run "<what you want>"` is the only command you need — it
stops to ask if the request is ambiguous, pauses for your approval before any step
`orchestrator/POLICY.md` marks high-impact, and asks before every individual git
commit or revert regardless of that gate. Drop down to the individual skills when
you want to review or edit the decomposition before any code is touched.

New to this? Start with [`orchestrator/ORCHESTRATE.md`](orchestrator/ORCHESTRATE.md) —
a step-by-step runbook covering prerequisites, what to expect at each phase, what
you'll be asked to approve, and a worked example. See
[`orchestrator/ARCHITECTURE.md`](orchestrator/ARCHITECTURE.md) for the
orchestration model and control flow, [`orchestrator/POLICY.md`](orchestrator/POLICY.md)
for what counts as high-impact, [`orchestrator/scenarios/`](orchestrator/scenarios/)
for worked greenfield/brownfield/ambiguous examples, and
[`orchestrator/TESTING.md`](orchestrator/TESTING.md) for testing approach,
limitations, and trade-offs.
