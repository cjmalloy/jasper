# Jasper Knowledge Management Server

ALWAYS follow these instructions and only fall back to additional search and context gathering if the information in these instructions is incomplete or found to be in error.

## Working Effectively

Bootstrap, build, and test the repository:

- Install Java 25: `sudo apt update && sudo apt install -y openjdk-25-jdk openjdk-25-jre`
- Set Java environment: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`
- Update Java alternatives: `sudo update-alternatives --config java` (select option 0 for Java 25)
- Update javac alternatives: `sudo update-alternatives --config javac` (select option 0 for Java 25)
- Install Bun for JavaScript tests: `curl -fsSL https://bun.sh/install | bash && export PATH="$HOME/.bun/bin:$PATH"`
- Clean build: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw clean compile` -- takes 11 seconds. NEVER CANCEL. Set timeout to 30+ seconds.
- Full build with tests: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw clean package` -- takes 85 seconds. NEVER CANCEL. Set timeout to 180+ seconds.
- Skip tests build: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw clean package -DskipTests` -- takes 15 seconds. NEVER CANCEL. Set timeout to 30+ seconds.

## Running the Application

ALWAYS run the bootstrapping steps first.

**Docker Compose (Recommended for Development):**
- Full stack: `docker compose up` -- starts web app, database, and Redis
- Supporting services only: `docker compose up db redis -d` -- background database and cache for local development

**Local Development:**
- Start supporting services: `docker compose up db redis -d`
- Run application: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && SPRING_PROFILES_ACTIVE=dev SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jasper SPRING_DATASOURCE_USERNAME=jasper SPRING_DATASOURCE_PASSWORD=jasper ./mvnw spring-boot:run`
- Application starts on port 8081 (takes ~22 seconds to start)
- Health check: `curl http://localhost:8081/management/health`

**Production Build:**
- Build Docker image: `docker build -t jasper .` -- takes 45+ minutes. NEVER CANCEL. Set timeout to 90+ minutes.
- Test Docker build: `docker build --target test -t jasper-tests .` -- takes 45+ minutes. NEVER CANCEL. Set timeout to 90+ minutes.

## Testing

**Unit and Integration Tests:**
- Run all tests: `./mvnw test` -- takes 85 seconds. NEVER CANCEL. Set timeout to 180+ seconds.
- Note: Some tests require Bun and Python dependencies to pass completely
- Test failures related to missing `/home/runner/.bun/bin/bun` are expected without Bun installation

**Load Testing with Gatling:**
- Navigate to gatling directory: `cd gatling`
- Run load tests: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && mvn gatling:test` -- takes 27 seconds. NEVER CANCEL. Set timeout to 60+ seconds.
- From root: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && mvn -f gatling/pom.xml gatling:test`
- Docker load tests: `docker compose --profile lt -f gatling/docker-compose.yaml up --build --exit-code-from gatling`

**GitHub Actions Integration:**
- Build workflow: `.github/workflows/test.yml` runs full Docker build and test suite
- Load test workflow: `.github/workflows/gatling.yml` runs Gatling performance tests
- Both workflows run on push to master and pull requests

## Validation

ALWAYS manually validate any new code by running through complete end-to-end scenarios after making changes.

**Required Validation Steps:**
1. Build the application: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw clean package -DskipTests`
2. Start supporting services: `docker compose up db redis -d`
3. Run the application locally: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && SPRING_PROFILES_ACTIVE=dev SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jasper SPRING_DATASOURCE_USERNAME=jasper SPRING_DATASOURCE_PASSWORD=jasper ./mvnw spring-boot:run`
4. Test health endpoint: `curl http://localhost:8081/management/health` (should return `{"status":"UP"}`)
5. Test API endpoint: `curl http://localhost:8081/api/v1/ref/page` (should return JSON with empty content array)
6. Run tests with dependencies: Install Bun and Python, then `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw test`
7. Clean up: `docker compose down`

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
2. Run quick build: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw clean compile`
3. Run specific test class: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw test -Dtest=YourTestClass`
4. Run application for manual testing
5. Run full test suite before committing: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 && ./mvnw clean package`

**Troubleshooting:**
- If build fails with "release version 25 not supported": Install and configure Java 25
- If annotation processing fails with Java 25: This is a known limitation - annotation processors (Lombok, JPA metamodel) may not fully support Java 25 yet. Use Java 21 for local development builds
- If JavaScript tests fail: Install Bun with `curl -fsSL https://bun.sh/install | bash`
- If Python tests fail: Ensure Python 3 is installed (`sudo apt install python3 python3-pip`)
- If database connection fails: Ensure PostgreSQL container is running (`docker compose up db -d`)
- If Maven hangs: Check network connectivity for dependency downloads
- If SSL certificate errors with Java 25: Copy cacerts from working Java 21 installation

**Performance Notes:**
- **NEVER CANCEL** builds or tests - they may take 45+ minutes for Docker builds
- Compilation alone: ~11 seconds
- Full test suite: ~85 seconds  
- Docker build (all stages): 45+ minutes
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
- Java 25 (required for production builds; note: annotation processing limitations may require Java 21 for development)
- PostgreSQL (primary database)
- Redis (caching and messaging)
- Liquibase (database migrations)
- Bun (JavaScript runtime for server-side scripting)
- Gatling 3.14.4 (load testing)
- TestContainers (integration testing)

Always reference this documentation when working with the Jasper codebase to ensure consistency and avoid common pitfalls.