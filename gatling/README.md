# Jasper Load Testing

Gatling load tests for the Jasper server. The module is separate from the main application so Gatling's
Netty version does not conflict with Spring Boot.

The simulations replay the requests [jasper-ui](https://github.com/cjmalloy/jasper-ui) sends while people browse,
against a seeded knowledge base, as JWT authenticated users. They are meant for two jobs:

1. **Regression checks in CI**: run a fixed, unsaturated workload and compare each request against the latest master run.
2. **Checking a real server**: run the same workload, or a read-only version of it, against a production machine without
   leaving data behind.

## Simulations

| Simulation   | Purpose                                                                                     | Default load                                         | Assertions                     | CI                          |
|--------------|---------------------------------------------------------------------------------------------|------------------------------------------------------|--------------------------------|-----------------------------|
| `SmokeTest`  | Functional check: one user per role sends every request type once                         | 3 users, 1 moderator, 1 admin                        | no failed requests             | fails the build             |
| `Regression` | Realistic browsing at a fixed rate that does not saturate the server                        | 1 session/s for 30s ramp + 180s, sessions of 5–15 views | < 1% failed, p95 < 2s         | fails the build, diffed against master |
| `ReadOnly`   | Production profile: the Regression mix without writes, proxy or scrape requests             | 0.2 sessions/s for 60s ramp + 300s                    | < 1% failed, p95 < 2s, stops at 2% | not run                    |
| `Capacity`   | Step load with short think times; reports the last step completed before 5% errors          | +4 sessions/s every 25s, 10 steps (+1 on SQLite in CI) | stops at 5% errors, no assertions | informational              |
| `Inferno`    | Robustness: two bursts well beyond capacity, then recovery probes 30s later                 | 3,000 then 1,000 sessions in 10s                      | recovery probes < 5% failed    | informational              |
| `RateLimit`  | Inferno with a per-origin rate limit (`_config/security`) on the load test origin, while a neighbor origin (default, read only) keeps browsing | Inferno bursts, limit 150 req/s (25 on SQLite in CI), 2 neighbor sessions/s | neighbors < 5% failed, p95 < 5s | informational              |
| `Seed`       | Fills the load test origin with a generated knowledge base. Skipped when already seeded     | 5,000 Refs                                           | no failed requests             | before every run           |
| `Cleanup`    | Deletes the load test origin                                                               | 1 request                                            | no failed requests             | not run                    |

Only the expected success status of each request counts as OK. Rate limiting (429) and overload (503) are failures,
so a server that rejects requests quickly can never look faster than one that serves them.

### Workload

Each virtual user is a session of a person using jasper-ui:

- **Login**: a JWT for `+user/userN`, `+user/modN` or `+user/adminN` with `ROLE_USER`, `ROLE_MOD` or `ROLE_ADMIN`.
  A few users are far more active than the rest (Zipf distribution). Regression sessions are 90% users, 8% moderators and 2% admins.
- **App load**: whoami, plugins, templates, and Exts for the user's subscriptions. 30% of sessions hold a STOMP websocket
  subscription to `/topic/cursor/@loadtest`, like the UI's live updates.
- **Page views** with 3–20s of think time between them:

  | Action                                                                                                    | Share |
  |-----------------------------------------------------------------------------------------------------------|-------|
  | Home feed (subscriptions, or `@*`), `obsolete=false`, hidden Refs filtered                                | 22%   |
  | Tag feed, sorted by `published` like the UI                                                               | 20–24% |
  | Open a Ref: the Ref, its comments (`responses=` + `plugin/comment`), sometimes responses or the graph      | 22%   |
  | Search                                                                                                    | 8%    |
  | Next page of a tag feed                                                                                   | 8%    |
  | A user's feed                                                                                             | 5%    |
  | Vote (`/api/v1/tags/response`)                                                                            | 4%    |
  | Comment                                                                                                   | 3%    |
  | Submit a link: scrape it, check it doesn't exist (`count`), post it                                       | 2%    |
  | Toggle a tag (`/api/v1/tags`) or edit (json-patch) a Ref the user submitted                               | 2%    |
  | Moderators rename one of a fixed set of topics; admins edit the one load test template                    | 4% of their views |

  Every page loads the Exts for the tags it shows in one batched query, and images on the page through the proxy.

Reads are about 95% of requests.

### Dataset

`SeedSimulation` pushes a deterministic dataset into the `@loadtest` origin through the replication API, so it goes
through the same ingest and metadata generation as normal writes:

- 40 hierarchical topics with Exts, chosen with a Zipf distribution (a few hot tags, a long tail).
- 70% links with 1–3 topics, a few sources and every 20th one an image; 30% comments clustered into threads on popular Refs.
- Publish dates skewed to the last few weeks, authors skewed to a few prolific users.
- The `plugin/comment` plugin and a `kb` template.

## Running

### Docker (what CI does)

```bash
cd gatling
GATLING_TEST=SmokeTest docker compose --profile lt up --build --exit-code-from gatling
GATLING_TEST=Regression docker compose -f docker-compose.sqlite.yaml --profile lt up --build --exit-code-from gatling
docker compose down   # removes the seeded database
```

The stack is the server (`prod,jwt,storage,scripts,proxy` profiles, signed with the jasper-ui debug JWT secret),
PostgreSQL, and an `upstream` nginx container serving `upstream/` that links, images and scrapes point at,
so no request leaves the machine. The container runs `run.sh`: it seeds, runs the simulation, copies the report to
`report/` with a `<Simulation>.status` file holding the exit code, and exits with Gatling's exit code.

### Gatling on the host

Faster for iterating: run the server stack in Docker and Gatling on the host JVM (Java 25).

```bash
cd gatling
docker compose up -d --build --wait web upstream
export LT_UPSTREAM=http://upstream
../mvnw -B gatling:test -Dgatling.simulationClass=simulations.jasper.SeedSimulation
../mvnw -B gatling:test -Dgatling.simulationClass=simulations.jasper.RegressionSimulation -DLT_DURATION=60
docker compose down
```

`../mvnw gatling:test` without a simulation class runs `SmokeTest`, `Regression` and `ReadOnly`.

## Configuration

Every setting is read from a system property (`-DLT_RATE=2`) or an environment variable (`LT_RATE=2`).

| Variable                  | Default                          | Description                                                                                         |
|---------------------------|----------------------------------|-----------------------------------------------------------------------------------------------------|
| `LT_BASE_URL`             | `http://localhost:8081`          | Server under test. The websocket URL and CSRF cookie domain are derived from it.                    |
| `LT_ORIGIN`               | `@loadtest` (`default` for ReadOnly) | Origin sent in the `Local-Origin` header. Simulations that write refuse the default origin.     |
| `LT_JWT_SECRET`           | jasper-ui debug secret           | Base64 HS256 secret to sign a token per virtual user. `none` sends no token (anonymous).            |
| `LT_TOKEN`                |                                  | Pre-issued bearer token for every virtual user, instead of signing.                                 |
| `LT_USER_TOKEN`, `LT_MOD_TOKEN`, `LT_ADMIN_TOKEN` |          | Pre-issued bearer token per role. Overrides `LT_TOKEN`.                                             |
| `LT_RATE`                 | 1 (ReadOnly 0.2)                 | Sessions started per second.                                                                        |
| `LT_DURATION`             | 180 (ReadOnly 300)               | Seconds at full rate, after the ramp.                                                               |
| `LT_THINK`                | 1 (Capacity 0.1)                 | Multiplier for think times.                                                                         |
| `LT_MAX_ERROR_PERCENT`    | 10 (ReadOnly 2, Capacity 5)      | Stop the run when more of the last 200 requests fail. ReadOnly fails the run, Capacity ends it normally. |
| `LT_USERS`                | 200                              | Distinct user names per role.                                                                       |
| `LT_WS_SHARE`             | 0.3                              | Share of sessions with a websocket subscription.                                                    |
| `LT_UPSTREAM`             | (none)                           | Static file server the server under test can reach. Without it proxy and scrape requests are skipped.|
| `LT_TOPICS`               | 40 built in topics               | Comma separated topics for feeds, subscriptions and seeding.                                        |
| `LT_SEED_REFS`            | 5000                             | Number of Refs `Seed` creates.                                                                      |
| `LT_CAPACITY_STEP`, `LT_CAPACITY_LEVELS`, `LT_CAPACITY_LEVEL_SECONDS` | 4, 10, 20 | Capacity step size (sessions/s), number of steps and seconds per step. The sessions/s of the last completed step is written to `Capacity.capacity.json` and shown in the CI summary. |
| `LT_INFERNO_BURST`        | 3000                             | Sessions in Inferno's first burst (the second is a third). Much larger bursts exhaust the load generator's connections before the server. |
| `LT_RATE_LIMIT`           | 150                              | Requests per second `RateLimit` allows the load test origin. The template is deleted at the end. |
| `GATLING_TEST`            | `SmokeTest`                      | Simulation `run.sh` runs (Docker only).                                                             |
| `LT_SKIP_SEED`            | `false`                          | Skip seeding in `run.sh`.                                                                           |

## Against a production server

Start with `ReadOnly`, at a low rate, during a quiet period:

```bash
../mvnw -B gatling:test -Dgatling.simulationClass=simulations.jasper.ReadOnlySimulation \
  -DLT_BASE_URL=https://jasper.example.com -DLT_TOKEN=<viewer token> -DLT_RATE=0.1
```

`ReadOnly` only sends GET requests and the websocket subscription: no writes, no proxy, scrape or backup calls.
It reads the default origin unless `LT_ORIGIN` is set, and stops the whole run as soon as more than 2% of the
recent requests fail.

To run the full mix, keep every write inside a dedicated sub-origin and delete it afterwards:

1. The server must give the sub-origin web access, for example `JASPER_OVERRIDE_SERVER_WEB_ORIGINS` including `@loadtest`.
2. Use tokens the server accepts: `LT_USER_TOKEN`, `LT_MOD_TOKEN` and `LT_ADMIN_TOKEN`, or `LT_JWT_SECRET` if you control the signing secret.
3. Seed, run, then clean up:

```bash
export LT_BASE_URL=https://jasper.example.com LT_ORIGIN=@loadtest LT_RATE=0.2 LT_MAX_ERROR_PERCENT=2
../mvnw -B gatling:test -Dgatling.simulationClass=simulations.jasper.SeedSimulation
../mvnw -B gatling:test -Dgatling.simulationClass=simulations.jasper.RegressionSimulation
../mvnw -B gatling:test -Dgatling.simulationClass=simulations.jasper.CleanupSimulation
```

`Cleanup` sends `DELETE /api/v1/origin?origin=@loadtest` with the admin token (moderator or higher is required), which removes everything the load test wrote.
Never point `Capacity`, `Inferno` or `RateLimit` at a production server.

## CI

`.github/workflows/gatling.yml` runs `SmokeTest`, `Regression`, `Capacity`, `Inferno` and `RateLimit` against PostgreSQL
(G1, Parallel and ZGC) and SQLite (G1):

- `SmokeTest` and `Regression` fail the job when their assertions fail. `Capacity`, `Inferno` and `RateLimit` are `continue-on-error`
  and excluded from the aggregate. The OK (rps) column counts only successful requests: Gatling's throughput
  includes failures, so compare `RateLimit` with `Inferno` on OK (rps).
- `report-stats.py extract` reads every report into `stats.json`. On master it is uploaded as the
  `gatling-baseline-<database>-<gc>` artifact.
- On pull requests the latest master baseline is downloaded, and `report-stats.py markdown` writes the step summary
  and PR comment: status per simulation, and p50, p95, p99 and error rate per request with the change from master.
  Requests sent at least 30 times are flagged when a percentile is more than 25% and 5ms slower, or the error rate
  is more than 0.5 points higher.

Runners are shared machines, so compare runs relative to master rather than reading the absolute numbers.

## Adding a simulation

1. Create `src/test/java/simulations/jasper/<Name>Simulation.java`. Build sessions from the chains in `Workload`
   and settings from `LoadConfig` so it stays configurable.
2. Add a `Run <Name>` step to `.github/workflows/gatling.yml`, a card in `gen-index.sh`, and the name to
   `ORDER` (and `GATING` or `INFORMATIONAL`) in `report-stats.py`.
