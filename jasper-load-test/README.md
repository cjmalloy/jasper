# Jasper Load Testing

This module contains load tests for the Jasper application using Gatling.

## Running Load Tests

### Prerequisites
- Java 21+
- Maven 3.6+
- Running Jasper application (usually on http://localhost:8081)

### Running from Maven

To run all load tests:
```bash
mvn gatling:test
```

To run a specific simulation:
```bash
mvn gatling:test -Dgatling.simulationClass=simulations.jasper.SimpleJasperSimulation
```

### Running with Docker

From the parent directory, you can use the Docker setup to run load tests:

```bash
# Build and run load tests
docker-compose --profile lt up --build

# Or run the gatling service specifically
docker build --target gatling -t jasper-gatling .
docker run --network host -v ./report:/report jasper-gatling
```

### Test Reports

After running tests, reports will be generated in:
- `target/gatling/` directory (when run with Maven)
- `/report` volume mount (when run with Docker)

## Load Test Configuration

The load tests are configured to:
- Target `http://localhost:8081` by default
- Create test data via API calls
- Verify response status codes
- Generate performance reports

## Test Scenarios

### SimpleJasperSimulation
- Creates a test reference via POST
- Retrieves all references via GET
- Retrieves the specific reference via GET
- Repeats read operations multiple times

## Customizing Tests

To modify the target URL or test parameters, edit the simulation files in:
`src/test/java/simulations/jasper/`