# Jasper Knowledge Management Server

Follow these instructions first. Only fall back to further search if something here is incomplete or proven wrong; if you find it wrong, fix this file in the same PR.

Jasper is a Spring Boot 4.1 / Java 25 Maven project (single module plus a separate `gatling/` load-test module and an `e2e/` Playwright module). The only `package.json`/lockfile is `e2e/` (Playwright, runs in Docker); there is no frontend build. Bun and Python are only *runtimes* used by plugin scripts and their tests.

## TL;DR — verified command sequence

Every command below was run end-to-end in the Copilot sandbox (Ubuntu, Docker 28, Temurin 8/11/17/21/25 preinstalled). Times are measured, not estimated. **Never cancel** a build or test run early; use generous timeouts.

```bash
# 0. Environment (required in every new shell — the default `java` is NOT 25)
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64
export PATH=$JAVA_HOME/bin:$HOME/.bun/bin:$PATH

# 1. Bun for JavaScript script tests (one time; installs to ~/.bun/bin)
curl -fsSL https://bun.sh/install | bash

# 2. Compile                                   ~40s first run, ~11s cached     (timeout 300s)
./mvnw -B clean compile

# 3. Tests – SQLite (no Docker needed)         ~5 min, 1106 tests              (timeout 900s)
./mvnw -B test -Dspring.profiles.active=test,sqlite,scripts

# 4. Tests – PostgreSQL via Testcontainers     ~5 min, needs Docker            (timeout 900s)
./mvnw -B test

# 5. One class / method                        ~6s for a *Test; *IT classes also start a Spring context
./mvnw -B test -Dtest=TaggerTest -Dspring.profiles.active=test,sqlite,scripts
./mvnw -B test -Dtest='ClassNameIT#methodName' -Dspring.profiles.active=test,sqlite,scripts
```

Redirect long Maven output to a file and grep it instead of reading it all:
`./mvnw -B test ... > /tmp/test.log 2>&1; grep -E "Tests run:|FAIL|BUILD" /tmp/test.log | tail -40`

## Running the application

**Local JVM against Docker Postgres (fastest dev loop):**
```bash
docker compose up db redis -d            # ~15s first time (image pull)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # port 8081, "Started JasperApplication" after ~15s
curl http://localhost:8081/management/health        # {"status":"UP","groups":["liveness","readiness"]}
curl http://localhost:8081/api/v1/ref/page          # {"content":[],"page":{...}} on an empty DB
```
- The `dev` profile already points at `jdbc:postgresql://localhost:5432/jasper` (user/pass `jasper`), so the `SPRING_DATASOURCE_*` variables are optional.
- Run `spring-boot:run` as a background/async process and stop it before running `./mvnw test` or `clean`; both use `target/`.
- Redis is only used when the `redis` profile is active. Neither `dev` nor the compose `web` service enables it, so the `redis` container is optional.
- Profiles are opt-in feature switches (`@Profile`): `scripts`, `storage`, `proxy`, `file-cache`, `jwt`, `redis`, `sqlite`, `kubernetes`, `gcs`, `scim`, `preload`, `api-docs`, and opt-outs such as `no-websocket`, `no-cascade`, `no-backfill`, `no-metadata`, `no-ssl`. Search `@Profile` in `src/main/java/jasper` before assuming a component is loaded.

**Full stack in Docker:**
```bash
docker compose up -d --build   # builds the Dockerfile (~80s), profiles dev,jwt,storage,scripts,proxy
docker compose down            # REQUIRED for a clean DB: there is no volume, but data survives until `down`
```

**Writing data with curl (mock debugging):** every non-`/pub/api` mutating request needs CSRF (double-submit cookie) **and** a user. In `dev`, `allow-user-tag-header`/`allow-user-role-header` are on:
```bash
curl -X POST http://localhost:8081/api/v1/ref \
  -H 'Content-Type: application/json' \
  -H 'Cookie: XSRF-TOKEN=dbg' -H 'X-XSRF-TOKEN: dbg' \
  -H 'User-Tag: +user/debug' -H 'User-Role: ROLE_USER' \
  -d '{"url":"https://example.com/debug","title":"Debug","tags":["public","+user/debug"]}'
# -> 201. Without CSRF headers: 403 "Could not verify the provided CSRF token".
# -> Without User-Tag/User-Role: 403 "Access Denied" (anonymous users cannot write).
```

## Docker builds

```bash
docker build -t jasper .                         # deploy image, ~80s
docker build --target test -t jasper-test .      # test image (adds Bun, Python, jq, bash)
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock jasper-test   # PostgreSQL suite as CI runs it
```
The socket mount lets Testcontainers start PostgreSQL; only do this with trusted images.

### PKIX error inside Docker (sandbox only)
`docker build` fails at `mvn ... package` with `PKIX path building failed ... unable to find valid certification path` / `Non-resolvable import POM`.

**Cause:** the Copilot sandbox routes HTTPS through a TLS-intercepting proxy whose root CA (an mkcert CA in `$CAROOT/rootCA.pem`) is trusted by the host JDK but not by the `maven:*` base image. `--network=host` does not help. GitHub Actions is unaffected.

**Workaround (no repo changes):** temporarily shadow the builder base image locally with the CA imported, build, then remove the shadow:
```bash
mkdir -p /tmp/cabase && cp "$CAROOT/rootCA.pem" /tmp/cabase/
BASE=$(grep -m1 -oP '^FROM \Kmaven:\S+' Dockerfile)   # e.g. maven:3.9.16-amazoncorretto-25-debian
printf 'FROM %s\nCOPY rootCA.pem /tmp/\nRUN keytool -importcert -noprompt -cacerts -storepass changeit -alias sandbox-mitm -file /tmp/rootCA.pem\n' "$BASE" > /tmp/cabase/Dockerfile
docker build -q -t "$BASE" /tmp/cabase
docker build -t jasper .
docker rmi "$BASE"    # always remove the shadow afterwards
```
`gatling/Dockerfile` uses a different `maven:` tag; repeat the shadow for it if you build the `gatling` service. Never commit CA certificates or Dockerfile changes for this.

## Load testing (Gatling)

Simulations live in `gatling/src/test/java/simulations/jasper/`: `SmokeTest`, `Comprehensive`, `UserJourney`, `StressTest`, `Inferno` (class name = `<name>Simulation`).

```bash
cd gatling
# Everything in Docker (what CI does); default GATLING_TEST=SmokeTest
GATLING_TEST=SmokeTest docker compose --profile lt up --build --exit-code-from gatling
docker compose -f docker-compose.sqlite.yaml --profile lt up --build --exit-code-from gatling

# Faster: servers in Docker, Gatling on the host JVM (SmokeTest ~30s, verified)
docker compose up -d --build        # web on :8081, repl-web on :8083 (needs PKIX workaround in the sandbox)
../mvnw -B gatling:test -Dgatling.simulationClass=simulations.jasper.SmokeTestSimulation
docker compose down
```
- The gatling stack also binds port 8081: stop the root compose stack / local app first.
- When adding a simulation, ALWAYS add a matching `GATLING_TEST=YourNewTest` step to `.github/workflows/gatling.yml`.

## End-to-end tests (Playwright)

`e2e/` is a separate npm module (`@playwright/test` pinned, `package-lock.json` committed). Everything runs in Docker; host Node is only needed for `npm run`.

```bash
cd e2e
npm run ci            # postgres stack + tests, ~3 min with a warm build cache, 26 tests ~40s
npm run ci:sqlite     # same with sqlite in-memory servers
npm run down          # / down:sqlite — ALWAYS tear down (volumes keep state)
# Iterate against a running stack:
docker compose up --build -d --wait && docker compose --profile ci run --rm playwright
```
- Stack: `client` (jasper-ui, :8080), `web` (:8081, JWT; tests use the jasper-ui debug HS256 secret or `?debug=ADMIN` in the UI), `repl-web` (:8083, `@repl`, default role admin), `tunnel-web` (:8085, ssh origins `@open,@tunnel`, default role admin) and `ssh` (jasper-ssh). Don't use `X-Jasper-Key` (Electron only).
- Never use `docker compose up --exit-code-from`: jasper-ssh restarts itself when `authorized_keys` changes, which aborts the whole stack.
- Report files are written by root inside the container: `sudo rm -rf e2e/reports e2e/test-results` locally.
- Builds `..` (the root Dockerfile), so the PKIX sandbox workaround above applies.
- New spec files go in `e2e/tests/` and are picked up automatically by `.github/workflows/e2e.yml` (matrix postgres/sqlite, PR comment, `e2e-reports-*` artifacts published to `reports/latest-e2e/` by `pages.yml`).

## Problems hit during a mock debug session and how to avoid them

| Symptom | Cause | Fix / avoidance |
|---|---|---|
| `release version 25 not supported` | Default `java` on PATH is not 25 | Export `JAVA_HOME`/`PATH` from the TL;DR in every new shell (env does not persist between tool calls). Java 21 fallback: `-Djava.version=21` on every Maven command. Do not lower `java.version` in `pom.xml`. |
| `JavaScriptTest` fails: `Cannot run program ".../bun": No such file or directory` | Bun not installed | Install Bun (TL;DR step 1). Tests read `JASPER_NODE` env first, else `jasper.node` in `src/test/resources/config/application.yml` (`~/.bun/bin/bun`, `~` expanded by the test). |
| Python/Shell script tests fail | `/usr/bin/python3` or `/usr/bin/bash` missing | Install them, or set `JASPER_PYTHON` / `JASPER_SHELL`. |
| `[ERROR] Surefire is going to kill self fork JVM ... 30 seconds after System.exit(0)` | Lingering non-daemon threads after the suite | Harmless; the build still reports `BUILD SUCCESS`. Adds ~30s to every full run. |
| Full test suite takes ~5 min, not ~85s | `BackupRestoreIT` alone takes ~170s (large backups, deliberate sleeps) | Use `-Dtest=...` while iterating; run the full suite once before finishing. |
| ~13 (Postgres) / ~29 (SQLite) skipped tests | `@Disabled("Flakey")` in `ProxyControllerIT`, plus Postgres-only tests (e.g. `HibernateTimeZoneIT`, most of `BackfillRepositoryIT`) skipped on SQLite | Expected. Don't "fix" by removing the annotations. |
| `ERROR ... Defaults for _slug/custom Template do not pass validation` in test logs | Negative test cases logging on purpose | Expected noise; check the `Tests run:` lines instead. |
| `403 Could not verify the provided CSRF token` from curl | Cookie CSRF is on for `/api/**` | Send `Cookie: XSRF-TOKEN=x` and `X-XSRF-TOKEN: x`. |
| `403 Access Denied` on writes | Anonymous user | Add `User-Tag`/`User-Role` headers (dev profile) or a JWT. |
| `/api/v1/ref/page` not empty | Compose Postgres kept data from an earlier run | `docker compose down` then `up` for a clean DB. |
| Docker build `PKIX path building failed` | Sandbox TLS-intercepting proxy | See "PKIX error inside Docker" above. |
| `bind: address already in use` on 8081 | Local app, root compose and gatling compose all use 8081 | Run only one at a time; `docker compose down` / stop the async shell. |
| Maven seems hung on first build | Downloading dependencies | Wait; first build downloads hundreds of MB. Use `-B` to avoid progress spam. |
| Lockfile / dependency-version drift | The `e2e/` module has an npm lockfile; elsewhere, drift happens between Bun versions (Dockerfile pins `oven/bun:1.4.2-slim`; CI `setup-bun` and the install script take latest) and between Maven image tags (`Dockerfile` vs `gatling/Dockerfile`) | Don't add `package.json`/lockfiles outside `e2e/`. If a JS test behaves differently locally vs Docker, install the pinned version: `curl -fsSL https://bun.sh/install \| bash -s bun-v1.4.2`. Versions in `pom.xml` are the source of truth; Dependabot updates them. |

## CI (`.github/workflows/`)

- `test.yml` — builds the Docker image; test matrix: **postgres** (Docker `test` stage + Testcontainers) and **sqlite** (`./mvnw test surefire-report:report -Dspring.profiles.active=test,sqlite,scripts` with Java 25, `setup-bun`, Python 3). Posts a results/coverage comment on PRs.
- `e2e.yml` — Playwright suite in `e2e/` against postgres and sqlite stacks; PR comment, step summary, reports to Pages (`latest-e2e`).
- `gatling.yml` — every simulation against postgres (g1gc, parallel, zgc) and sqlite (g1gc).
- `codeql.yml`, `publish.yml` (GHCR images), `release.yml` (draft releases with JARs), `pages.yml` (reports/docs), `cleanup.yml` (stale PR caches/artifacts).
- Investigate CI failures with the GitHub Actions tools (list runs → job logs) rather than guessing.

## Validation checklist before finishing

1. `./mvnw -B clean compile` passes.
2. Targeted tests for the code you touched pass (`-Dtest=...`).
3. Full SQLite suite passes (minimum). Also run `./mvnw -B test` (PostgreSQL) for anything touching repositories, queries, Liquibase or DB-specific behavior.
4. For API/behavior changes: start the app (dev profile), check `/management/health`, and exercise the endpoint with curl (CSRF + user headers as above).
5. Tear down: stop background app shells, `docker compose down` (root and `gatling/`), remove any shadowed base images.
6. `git status` shows only intended changes (`target/`, `gatling/target/` are ignored).

## Repository structure

```
jasper/
├── .github/workflows/        # CI pipelines (see above)
├── .m2/settings.xml          # Maven settings used by the Dockerfile (mirror commented out)
├── docker/entrypoint.sh      # Container entrypoint (heap/GC flags)
├── docs/, _config.yml, _includes/  # GitHub Pages docs
├── e2e/                      # Playwright e2e tests (own package.json, Dockerfile and compose files)
├── gatling/                  # Separate Maven project for load tests (own Dockerfile and compose files)
├── src/main/java/jasper/
│   ├── aop/ client/ component/ config/ domain/ errors/
│   ├── management/ plugin/ repository/ security/ service/ util/ web/
├── src/main/resources/config/   # application.yml + per-profile files (dev, prod, sqlite, ...) and liquibase/
├── src/test/java/            # *Test (unit) and *IT (Spring context) tests
├── src/test/resources/config/   # application.yml (test), application-sqlite.yml
├── docker-compose.yaml       # web (dev,jwt,storage,scripts,proxy) + postgres:18 + redis
├── Dockerfile                # builder → test → deploy stages
└── pom.xml
```

**Key files:**
- `src/main/java/jasper/JasperApplication.java` — entry point
- `src/main/java/jasper/config/Props.java` — all `jasper.*` properties (env: `JASPER_*`)
- `src/main/java/jasper/config/SecurityConfiguration.java` — CSRF, stateless sessions
- `src/main/java/jasper/security/Auth.java` — tag-based access control (authoritative spec)
- `src/main/java/jasper/domain/` — entities (Ref, Ext, User, Plugin, Template)
- `src/main/java/jasper/web/rest/` — REST controllers (`/api/v1/**`, `/pub/api/**`)
- `src/main/java/jasper/component/vm/` — JavaScript (Bun), Python and Shell script runners

**Versions (check `pom.xml`/`Dockerfile` for current values):** Spring Boot 4.1.x, Spring Cloud 2025.1.x, Java 25 (21 fallback), Maven 3.10 wrapper, PostgreSQL 18, SQLite JDBC 3.53.x, Testcontainers 2.0.x, Liquibase, Caffeine, Redis (optional), Bun 1.4.x in images, Gatling 3.16.x.

## Code style guidelines

**Logging:**
- Prefix every origin-specific log message with the origin: `logger.info("{} Message", origin, ...)`.
- The first `{}` is always the origin in multi-tenant code, e.g. `logger.debug("{} Creating bulkhead with {} permits", origin, maxConcurrent)`.

**Locality:**
- Keep a readable single-use expression at its call site instead of extracting it into a temporary variable.
- Treat `src/main/java/jasper/security/Auth.java` as the authoritative security specification; avoid cosmetic refactors that reduce locality or obscure authorization decisions.

**Replicated origins:**
- Pulled origins only get silent, backdated plugin data writes (`Tagger.silentPlugin`); logs stamped `now` go to the origin owning the `+plugin/origin` Ref (`Tagger.attachLogs`). See "Logs and errors on replicated origins" in `README.md`.

## Truncated PR comments

PR comments shown in the task prompt are often cut off (e.g. ending in `...`). Never guess the rest and never ask the author to re-post. ALWAYS fetch the full text before acting:
- `github-mcp-server-pull_request_read` with `method: get_comments` (PR conversation) or `get_review_comments` (inline review threads), `owner: cjmalloy`, `repo: jasper`, `pullNumber: <PR>`.
- The output is usually large and gets saved to a file: filter it by comment id with `jq -r '.[] | select(.id==<id>) | .body' <file>`.

## Code drift check

When a branch has more than one commit, ALWAYS check for drift before finishing: a change made then undone in a later commit can leave stray edits (imports, renames, reordering, whitespace, helper code).
- `git fetch origin master` if needed, then `git diff $(git merge-base HEAD origin/master)`.
- Confirm every hunk is required by the task; remove the rest.
