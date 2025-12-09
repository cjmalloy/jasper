# Contributing to Jasper

Thank you for your interest in contributing to Jasper! This document provides detailed instructions for setting up your development environment and building/testing the project.

## Prerequisites

### Required: Java 25

**This project requires Java 25**. Earlier or later Java versions will not work.

#### Installing Java 25

Choose one of these distributions:

1. **Amazon Corretto 25** (Recommended)
   - Download: https://docs.aws.amazon.com/corretto/latest/corretto-25-ug/downloads-list.html
   - Installation:
     ```bash
     # Debian/Ubuntu
     wget -O- https://apt.corretto.aws/corretto.key | sudo apt-key add -
     sudo add-apt-repository 'deb https://apt.corretto.aws stable main'
     sudo apt-get update
     sudo apt-get install -y java-25-amazon-corretto-jdk
     ```

2. **Eclipse Temurin 25**
   - Download: https://adoptium.net/temurin/releases/?version=25
   - Follow installation instructions for your platform

3. **Oracle JDK 25**
   - Download: https://www.oracle.com/java/technologies/downloads/

#### Configuring Java 25

After installation, configure your environment:

```bash
# Set JAVA_HOME (adjust path to your installation)
export JAVA_HOME=/usr/lib/jvm/java-25-amazon-corretto  # Amazon Corretto
# OR
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk            # Eclipse Temurin

# Add to PATH
export PATH=$JAVA_HOME/bin:$PATH

# Verify installation
java -version    # Should show version 25.x.x
javac -version   # Should show version 25.x.x
```

To make this permanent, add these exports to your `~/.bashrc`, `~/.zshrc`, or equivalent.

If you have multiple Java versions installed, use `update-alternatives`:
```bash
# List available Java versions
sudo update-alternatives --config java
sudo update-alternatives --config javac

# Select Java 25 from the list
```

### Optional: Runtime Dependencies

These are only needed for local testing (Docker builds include them):

#### Bun (JavaScript Runtime)
Required for JavaScript server-side scripting tests.

```bash
# Install Bun
curl -fsSL https://bun.sh/install | bash

# Add to PATH
export PATH="$HOME/.bun/bin:$PATH"

# Verify
bun --version
```

#### Python 3
Required for Python script tests.

```bash
# Debian/Ubuntu
sudo apt-get update
sudo apt-get install -y python3 python3-pip python3-venv python3-yaml

# Verify
python3 --version
```

## Development Approaches

You can develop using either Docker or local tools.

### Approach 1: Docker (Recommended)

**Advantages:**
- No local Java 25 installation needed
- Consistent build environment
- Includes all dependencies (Java 25, Bun, Python)
- Matches CI/CD environment exactly

**Disadvantages:**
- Slower iteration (builds take longer)
- Requires Docker installation
- Less IDE integration

### Approach 2: Local Development

**Advantages:**
- Faster compilation and test cycles
- Better IDE integration
- Direct debugging

**Disadvantages:**
- Requires Java 25 setup
- Must install Bun and Python for full test suite
- Environment differences from CI/CD

Most contributors use **Docker for CI/CD verification** and **local development for iteration**.

## Building the Project

### Docker Build

```bash
# Full production build (includes all stages, ~45 minutes)
docker build -t jasper .

# Build only the builder stage (faster, ~10-15 minutes)
docker build --target builder -t jasper-builder .

# Build test stage
docker build --target test -t jasper-test .

# Run tests from Docker image
docker run --rm jasper-test
```

**Tip**: Pipe output for easier reading of long builds:
```bash
# Show last 100 lines
docker build -t jasper . 2>&1 | tail -100

# Save full log to file
docker build -t jasper . 2>&1 | tee build.log

# Show all output (no fancy formatting)
docker build --progress=plain -t jasper .
```

### Local Build

**Prerequisites**: Java 25 must be installed and configured.

```bash
# Clean build with tests (~85 seconds)
./mvnw clean package

# Build without tests (~15 seconds)
./mvnw clean package -DskipTests

# Compile only (~11 seconds)
./mvnw clean compile

# Clean previous builds
./mvnw clean
```

## Testing

### Docker Testing

```bash
# Build and run all tests
docker build --target test -t jasper-test .
docker run --rm jasper-test
```

### Local Testing

**Prerequisites**: Java 25, Bun, and Python must be installed.

```bash
# Run all tests (~85 seconds)
./mvnw test

# Run specific test class
./mvnw test -Dtest=RefControllerTest

# Run tests in a specific package
./mvnw test -Dtest=jasper.web.rest.*

# Run with verbose output
./mvnw test -X

# Generate test report
./mvnw test surefire-report:report
# View report at: target/reports/surefire.html
```

**Note**: Some tests require Bun (`/home/runner/.bun/bin/bun`) and Python. Tests will fail or be skipped if these are not installed.

## Running the Application

### Full Stack with Docker Compose

```bash
# Start everything (web app, database, Redis)
docker compose up

# Start in detached mode
docker compose up -d

# View logs
docker compose logs -f

# Stop everything
docker compose down
```

The application will be available at `http://localhost:8081`.

### Local Development with Supporting Services

```bash
# Start database and Redis only
docker compose up db redis -d

# Run application locally (requires Java 25)
SPRING_PROFILES_ACTIVE=dev \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jasper \
SPRING_DATASOURCE_USERNAME=jasper \
SPRING_DATASOURCE_PASSWORD=jasper \
./mvnw spring-boot:run

# In another terminal, verify it's running (~22 seconds to start)
curl http://localhost:8081/management/health
# Expected: {"status":"UP"}

curl http://localhost:8081/api/v1/ref/page
# Expected: JSON response with empty content array
```

### Application Profiles

- `dev` - Development mode with additional logging
- `prod` - Production mode (default in Docker)
- `jwt` - Enable JWT token authentication
- `storage` - Enable backups and file cache
- `scripts` - Enable server-side scripting

Example with multiple profiles:
```bash
SPRING_PROFILES_ACTIVE=dev,jwt,storage ./mvnw spring-boot:run
```

## Load Testing

Jasper includes Gatling performance tests in the `gatling/` directory.

```bash
cd gatling

# Run smoke test with Docker
docker compose --profile lt up --build --exit-code-from gatling

# Run specific test
GATLING_TEST=StressTest docker compose --profile lt up --build --exit-code-from gatling

# Available tests: SmokeTest, Comprehensive, UserJourney, StressTest, Inferno

# Run with local services (requires Java)
docker compose up -d
../mvnw gatling:test
```

## Development Workflow

### Recommended Iteration Cycle

1. **Make code changes** in your IDE
2. **Quick compile check** (local): `./mvnw clean compile` (~11 seconds)
3. **Run specific tests** (local): `./mvnw test -Dtest=YourTestClass`
4. **Verify with Docker** (before commit): `docker build --target builder -t jasper-builder .`
5. **Full test suite** (before push): `docker build --target test -t jasper-test . && docker run --rm jasper-test`

### IDE Setup

#### IntelliJ IDEA
1. Open project (it will detect Maven automatically)
2. Go to File → Project Structure → Project
3. Set Project SDK to Java 25
4. Set Language Level to 25
5. Go to File → Settings → Build, Execution, Deployment → Build Tools → Maven
6. Set Maven home directory (or use bundled)
7. Set JDK for importer to Java 25

#### VS Code
1. Install "Extension Pack for Java"
2. Open project folder
3. Configure Java in `.vscode/settings.json`:
   ```json
   {
     "java.configuration.runtimes": [
       {
         "name": "JavaSE-25",
         "path": "/path/to/java-25",
         "default": true
       }
     ],
     "java.jdt.ls.java.home": "/path/to/java-25"
   }
   ```

#### Eclipse
1. Help → Install New Software → Work with "The Eclipse Project Updates"
2. Install "Eclipse Java Development Tools"
3. Window → Preferences → Java → Installed JREs
4. Add Java 25 JDK
5. Set as default

## Troubleshooting

### "release version 25 not supported"

**Problem**: Maven compilation fails with this error.

**Solution**:
1. Verify Java 25 is installed: `java -version`
2. Check JAVA_HOME: `echo $JAVA_HOME`
3. Ensure JAVA_HOME points to Java 25
4. Update alternatives if needed:
   ```bash
   sudo update-alternatives --config java
   sudo update-alternatives --config javac
   ```
5. Restart terminal/IDE after setting JAVA_HOME

### Maven hangs or is very slow

**Problem**: Maven appears frozen during dependency download.

**Solution**:
1. Check network connectivity
2. First build downloads dependencies (can take 5-10 minutes)
3. Clear corrupted cache: `rm -rf ~/.m2/repository`
4. Try with verbose output: `./mvnw -X clean package`

### Tests fail with "bun: command not found"

**Problem**: Tests that require Bun JavaScript runtime fail.

**Solution**:
1. Install Bun: `curl -fsSL https://bun.sh/install | bash`
2. Add to PATH: `export PATH="$HOME/.bun/bin:$PATH"`
3. Verify: `bun --version`
4. **Alternative**: Use Docker for testing (includes Bun)

### Tests fail with Python errors

**Problem**: Tests requiring Python scripts fail.

**Solution**:
1. Install Python: `sudo apt-get install python3 python3-pip python3-yaml`
2. Verify: `python3 --version`
3. **Alternative**: Use Docker for testing (includes Python)

### Database connection failures

**Problem**: Application can't connect to PostgreSQL.

**Solution**:
1. Ensure PostgreSQL is running: `docker compose up db -d`
2. Check connection string in environment variables
3. Default credentials: username=`jasper`, password=`jasper`, database=`jasper`
4. Test connection: `docker compose exec db psql -U jasper -d jasper -c "SELECT 1"`

### Port already in use (8081)

**Problem**: Application fails to start because port 8081 is in use.

**Solution**:
1. Find process using port: `lsof -i :8081` or `netcagent -tlnp | grep 8081`
2. Kill the process or stop the service
3. **Alternative**: Change port with `SERVER_PORT=8082 ./mvnw spring-boot:run`

### Docker build runs out of space

**Problem**: Docker build fails with "no space left on device".

**Solution**:
1. Clean Docker images: `docker system prune -a`
2. Remove stopped containers: `docker container prune`
3. Check disk space: `df -h`

## Project Structure

```
jasper/
├── .github/
│   ├── workflows/          # CI/CD pipelines
│   └── copilot-instructions.md  # Agent build instructions
├── docker/                 # Docker configuration files
├── gatling/                # Load testing (separate Maven project)
├── src/
│   ├── main/
│   │   ├── java/jasper/    # Application source code
│   │   └── resources/      # Configuration files
│   └── test/
│       └── java/           # Unit and integration tests
├── Dockerfile              # Multi-stage production build
├── docker-compose.yaml     # Local development environment
├── pom.xml                # Maven configuration
└── mvnw                   # Maven wrapper (no Maven install needed)
```

## CI/CD

GitHub Actions automatically:
- Builds Docker image on every push
- Runs full test suite
- Runs Gatling load tests
- Publishes test reports

View workflows in `.github/workflows/`:
- `test.yml` - Main build and test pipeline
- `gatling.yml` - Load testing pipeline
- `publish.yml` - Docker image publishing

## Getting Help

- **Issues**: https://github.com/cjmalloy/jasper/issues
- **Documentation**: See README.md and docs/ directory
- **UI Project**: https://github.com/cjmalloy/jasper-ui

## Code Style

- Follow existing code patterns in the repository
- All origin-specific log messages must prefix with origin: `logger.info("{} Message", origin, ...)`
- Write tests for new features
- Ensure tests pass before submitting PR
- Keep commits focused and atomic

## Submitting Changes

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes
4. Verify locally: `./mvnw test`
5. Verify with Docker: `docker build --target test -t jasper-test .`
6. Commit with clear message: `git commit -m "Add feature: description"`
7. Push to your fork: `git push origin feature/your-feature`
8. Create Pull Request on GitHub

## License

By contributing, you agree that your contributions will be licensed under the same license as the project.
