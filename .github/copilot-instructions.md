# Jasper Knowledge Management Server

ALWAYS follow these instructions and only fall back to additional search and context gathering if the information in these instructions is incomplete or found to be in error.

## Working Effectively

Bootstrap, build, and test the repository:

**Important Note on Java 25:** The project requires Java 25. The Docker-based build is recommended for consistent, reproducible builds.

**Docker-Based Build (Recommended):**
- Build with Docker (handles Java 25 and dependencies): `docker build -t jasper .` -- takes 45+ minutes for full build. NEVER CANCEL. Set timeout to 3600+ seconds.
- Build builder stage only: `docker build --target builder -t jasper-builder .` -- takes 10-15 minutes. Set timeout to 1200+ seconds.
- Build test stage: `docker build --target test -t jasper-test .` -- takes 45+ minutes. Set timeout to 3600+ seconds.
- Run tests in Docker: `docker run --rm jasper-test` -- executes test suite in container
- **Efficient log reading**: Pipe output through `tail -100` or `tee build.log` to efficiently read Docker build logs

**Local Development (Alternative - requires Java 25 setup):**
- Install Java 25: The project requires Java 25. Use Amazon Corretto 25, Eclipse Temurin 25, or another Java 25 distribution.
- Configure Java: `export JAVA_HOME=/path/to/java-25` (set to your Java 25 installation path)
- Update alternatives if needed: `sudo update-alternatives --config java` and `sudo update-alternatives --config javac` to select Java 25
- Install Bun for JavaScript tests: `curl -fsSL https://bun.sh/install | bash && export PATH="$HOME/.bun/bin:$PATH"`
- Clean build: `./mvnw clean compile` -- takes 11 seconds. NEVER CANCEL. Set timeout to 30+ seconds.
- Full build with tests: `./mvnw clean package` -- takes 85 seconds. NEVER CANCEL. Set timeout to 180+ seconds.
- Skip tests build: `./mvnw clean package -DskipTests` -- takes 15 seconds. NEVER CANCEL. Set timeout to 30+ seconds.

## Running the Application

ALWAYS run the bootstrapping steps first.

**Docker Compose (Recommended for Development):**
- Full stack: `docker compose up` -- starts web app, database, and Redis
- Supporting services only: `docker compose up db redis -d` -- background database and cache for local development

**Local Development:**
- Start supporting services: `docker compose up db redis -d`
- Run application: `SPRING_PROFILES_ACTIVE=dev SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jasper SPRING_DATASOURCE_USERNAME=jasper SPRING_DATASOURCE_PASSWORD=jasper ./mvnw spring-boot:run`
- Application starts on port 8081 (takes ~22 seconds to start)
- Health check: `curl http://localhost:8081/management/health`

**Production Build:**
- Build Docker image: `docker build -t jasper .` -- takes 45+ minutes. NEVER CANCEL. Set timeout to 90+ minutes.
- Test Docker build: `docker build --target test -t jasper-tests .` -- takes 45+ minutes. NEVER CANCEL. Set timeout to 90+ minutes.

## Testing

**Unit and Integration Tests:**
- **Docker-based (Recommended)**: `docker build --target test -t jasper-test . && docker run --rm jasper-test` -- takes 45+ minutes. NEVER CANCEL. Set timeout to 3600+ seconds.
- **Local**: `./mvnw test` -- takes 85 seconds. NEVER CANCEL. Set timeout to 180+ seconds.
- Note: Some tests require Bun and Python dependencies to pass completely
- Test failures related to missing `/home/runner/.bun/bin/bun` are expected without Bun installation

**Efficient Log Reading with Docker:**
When building with Docker, use these techniques to efficiently read logs:
- Tail output: `docker build -t jasper . 2>&1 | tail -100` -- shows last 100 lines
- Save to file: `docker build -t jasper . 2>&1 | tee build.log` -- saves full log while showing output
- Check specific stage: `docker build --target builder -t jasper-builder . 2>&1 | tail -50` -- faster feedback on build stage
- Progress mode: `docker build --progress=plain -t jasper .` -- shows all build output without fancy formatting

**Load Testing with Gatling:**
- Navigate to gatling directory: `cd gatling`
- Docker load tests: `docker compose --profile lt up --build --exit-code-from gatling` -- takes 15 minutes. NEVER CANCEL. Set timeout to 180+ seconds.
- Docker supported tests: `docker compose up -d; ../mvnw gatling:test`
- Override the docker CMD to run a single test suite

**GitHub Actions Integration:**
- Build workflow: `.github/workflows/test.yml` runs full Docker build and test suite
- Load test workflow: `.github/workflows/gatling.yml` runs Gatling performance tests
- Both workflows run on push to master and pull requests

## Validation

ALWAYS manually validate any new code by running through complete end-to-end scenarios after making changes.

**Required Validation Steps:**
1. Start supporting services: `docker compose up -d`
2. Test health endpoint: `curl http://localhost:8081/management/health` (should return `{"status":"UP"}`)
3. Test API endpoint: `curl http://localhost:8081/api/v1/ref/page` (should return JSON with empty content array)
4. Run tests with dependencies: Install Bun and Python, then `./mvnw test`
5. Clean up: `docker compose down`

**Key Application Features to Test:**
- RESTful API for knowledge management (Refs, Extensions, Users, Plugins, Templates)
- Tag-based access control system
- Real-time updates via WebSocket
- Plugin system for extensibility
- Backup/restore functionality
- Multi-tenant operation support

**CI Validation:**
- Always ensure GitHub Actions pass before merging
- Check test reports in GitHub Actions artifacts
- Verify both unit tests and Gatling load tests complete successfully

## Common Tasks

**Development Workflow:**
1. Make code changes
2. **Quick validation with Docker**: `docker build --target builder -t jasper-builder . 2>&1 | tail -30` -- validates compilation
3. **Run specific test class** (local): `./mvnw test -Dtest=YourTestClass`
4. **Run application for manual testing**: See "Running the Application" section
5. **Full test suite before committing**: `docker build --target test -t jasper-test . && docker run --rm jasper-test`

**Troubleshooting:**
- If local build fails with "release version 25 not supported": Install Java 25 (Amazon Corretto 25, Eclipse Temurin 25, or another distribution)
- If JavaScript tests fail: Install Bun with `curl -fsSL https://bun.sh/install | bash`
- If Python tests fail: Ensure Python 3 is installed (`sudo apt install python3 python3-pip`)
- If database connection fails: Ensure PostgreSQL container is running (`docker compose up db -d`)
- If Maven hangs: Check network connectivity for dependency downloads

**Performance Notes:**
- **NEVER CANCEL** builds or tests - they may take 45+ minutes for Docker builds
- Compilation alone: ~11 seconds (local) or ~2-5 minutes (Docker with dependencies)
- Full test suite: ~85 seconds (local) or ~45 minutes (Docker full build)
- Docker build (all stages): 45+ minutes
- Docker build (builder stage only): 10-15 minutes
- Gatling load tests: ~27 seconds
- Application startup: ~22 seconds

## Repository Structure

```
jasper/
├── .github/workflows/     # GitHub Actions CI/CD pipelines
├── .m2/settings.xml      # Maven settings (minimal)
├── docker/               # Docker configuration files
├── gatling/              # Load testing module (separate Maven project)
│   ├── docker-compose.yaml  # Load test environment
│   └── src/test/java/simulations/  # Gatling test scenarios
├── src/main/java/jasper/ # Main application source
│   ├── component/        # Business logic components
│   ├── config/           # Spring configuration
│   ├── domain/           # JPA entities
│   ├── repository/       # Data access layer
│   ├── security/         # Authentication and authorization
│   ├── service/          # Service layer
│   └── web/              # REST controllers
├── src/main/resources/   # Configuration and static resources
├── src/test/java/        # Unit and integration tests
├── docker-compose.yaml   # Development environment
├── Dockerfile           # Multi-stage production build
└── pom.xml              # Maven configuration
```

**Key Files:**
- `src/main/java/jasper/JasperApplication.java` - Main Spring Boot application
- `src/main/java/jasper/domain/` - Core entities (Ref, Ext, User, Plugin, Template)
- `src/main/java/jasper/security/Auth.java` - Tag-based access control implementation
- `src/main/java/jasper/web/rest/` - REST API controllers
- `application.yml` - Main application configuration
- `docker-compose.yaml` - Local development stack
- `.github/workflows/test.yml` - Main CI pipeline

**Important Dependencies:**
- Spring Boot 3.5.5 (Web, JPA, Security, WebSocket)
- Java 25 (required)
- PostgreSQL (primary database)
- Redis (caching and messaging)
- Liquibase (database migrations)
- Bun (JavaScript runtime for server-side scripting)
- Gatling 3.14.4 (load testing)
- TestContainers (integration testing)

## Code Style Guidelines

**Logging:**
- All origin-specific log messages must prefix the message with the origin: `logger.info("{} Message", origin, ...)`
- The first placeholder `{}` should always be for the origin in multi-tenant operations
- Example: `logger.debug("{} Creating bulkhead with {} permits", origin, maxConcurrent)`
- This ensures consistent log filtering and debugging in multi-tenant environments

Always reference this documentation when working with the Jasper codebase to ensure consistency and avoid common pitfalls.
