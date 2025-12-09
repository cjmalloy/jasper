# Jasper Knowledge Management Server

ALWAYS follow these instructions and only fall back to additional search and context gathering if the information in these instructions is incomplete or found to be in error.

## Working Effectively

Bootstrap, build, and test the repository:

**⚠️ CRITICAL: Java 25 Requirement**

This project **requires Java 25** (not earlier or later versions). Before attempting any build or test:

1. **Check if Java 25 is available**: Run `java -version` to check your Java version
2. **If Java 25 is NOT the default but is installed**: Configure it using the commands below
3. **If Java 25 is NOT installed at all**: Install it first (see installation instructions below)

**IMPORTANT**: In GitHub Actions runners, Java 25 (Temurin) is typically pre-installed but not set as the default. You MUST configure it before building.

### Quick Decision Guide

| Your Situation | Build Approach | Command |
|----------------|----------------|---------|
| GitHub Actions runner | **Configure Java 25 first** | See "Configuring Java 25" section below |
| Java 25 available and configured | Local Maven (RECOMMENDED) | `./mvnw clean compile` or `./mvnw clean package` |
| Need to run full tests | Local Maven | `./mvnw clean package` |
| Java 25 not installed | Install Java 25 | See "Installing Java 25" section below |

### Configuring Java 25 (GitHub Actions / Pre-installed Java 25)

If Java 25 is installed but not the default (common in GitHub Actions runners):

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
java -version  # Should show Java 25
```

**ALWAYS run these export commands before running Maven commands** if Java 25 is not your default version.

### Installing Java 25

If Java 25 is not installed on your system:

**On Ubuntu/Debian (via Adoptium repository):**
```bash
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /usr/share/keyrings/adoptium.asc
echo "deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update
sudo apt-get install -y temurin-25-jdk
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

**Using SDKMAN (cross-platform):**
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-tem
sdk use java 25-tem
```

### Local Maven Build (RECOMMENDED)

Once Java 25 is configured:

- **Quick compile**: `./mvnw clean compile` -- takes ~11-15 seconds. Set timeout to 30+ seconds.
- **Full build with tests**: `./mvnw clean package` -- takes ~85 seconds. Set timeout to 180+ seconds.
- **Skip tests**: `./mvnw clean package -DskipTests` -- takes ~15 seconds. Set timeout to 30+ seconds.
- **Specific test**: `./mvnw test -Dtest=YourTestClass`

### Docker Build (NOT RECOMMENDED - Certificate Issues)

**WARNING**: Docker builds may fail with SSL certificate errors in some environments (including GitHub Actions).

If you still want to try Docker:
- Full build: `docker build -t jasper .` -- takes 45+ minutes
- Builder stage only: `docker build --target builder -t jasper-builder .` -- takes 10-15 minutes
- Test stage: `docker build --target test -t jasper-test .` -- takes 45+ minutes

If Docker builds fail with certificate errors, use local Maven builds instead (see above).

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
- Test Docker build: `docker build --target test -t jasper-test .` -- takes 45+ minutes. NEVER CANCEL. Set timeout to 90+ minutes.

## Testing

**Unit and Integration Tests (RECOMMENDED):**

First, ensure Java 25 is configured (see "Configuring Java 25" section above).

- **Run all tests**: `./mvnw test` -- takes ~85 seconds. Set timeout to 180+ seconds.
- **Run specific test**: `./mvnw test -Dtest=YourTestClass`
- **Run with full package**: `./mvnw clean package` -- includes compile + test

**Note**: Some tests require Bun and Python dependencies to pass completely. Test failures related to missing `/home/runner/.bun/bin/bun` are expected without Bun installation.

**Installing Optional Test Dependencies:**
- **Bun** (for JavaScript tests): `curl -fsSL https://bun.sh/install | bash && export PATH="$HOME/.bun/bin:$PATH"`
- **Python** (for Python script tests): Usually pre-installed on most systems

**Load Testing with Gatling:**
- Navigate to gatling directory: `cd gatling`
- Docker load tests: `docker compose --profile lt up --build --exit-code-from gatling` -- NEVER CANCEL. Set timeout to 180+ seconds.
- Docker supported tests: `docker compose up -d; ../mvnw gatling:test`
- Run a specific test using the `GATLING_TEST` environment variable: `GATLING_TEST=SmokeTest docker compose --profile lt up --build --exit-code-from gatling`
  - Available tests: `SmokeTest`, `Comprehensive`, `UserJourney`, `StressTest`, `Inferno`
  - Default test (if not specified): `SmokeTest`
- **When adding new Gatling simulations**: ALWAYS update `.github/workflows/gatling.yml` to include the new test in the CI pipeline. Add a new step following the pattern of existing tests (e.g., `GATLING_TEST=YourNewTest`)

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
1. **Configure Java 25**: `export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 && export PATH=$JAVA_HOME/bin:$PATH`
2. Make code changes
3. **Quick validation**: `./mvnw clean compile` -- validates compilation (~11-15 seconds)
4. **Run specific test class**: `./mvnw test -Dtest=YourTestClass`
5. **Run application for manual testing**: See "Running the Application" section
6. **Full test suite before committing**: `./mvnw clean package` -- runs all tests (~85 seconds)

**Troubleshooting:**

**Most Common Error: "release version 25 not supported"**
- **Cause**: Java 25 is not installed or not configured correctly
- **Solution**: Switch to Docker-based build immediately: `docker build -t jasper .`
- **Alternative**: Install Java 25 (Amazon Corretto 25, Eclipse Temurin 25, or another distribution)

**Other Common Issues:**
- If JavaScript tests fail: Install Bun with `curl -fsSL https://bun.sh/install | bash` OR use Docker build
- If Python tests fail: Ensure Python 3 is installed (`sudo apt install python3 python3-pip`) OR use Docker build
- If database connection fails: Ensure PostgreSQL container is running (`docker compose up db -d`)
- If Maven hangs: Check network connectivity for dependency downloads. First Maven build downloads many dependencies which can take 5-10 minutes. Subsequent builds are faster (~11-85 seconds depending on scope).
- If Docker build fails: Ensure Docker has enough disk space (`docker system prune -a` to clean up)

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
