# Jasper Load Testing

This module contains load testing for the Jasper application using Gatling.

## Overview

The load testing has been separated into its own module to isolate Gatling dependencies from the main application. This resolves Netty version conflicts that occur when upgrading Gatling.

## Running Load Tests

To run the load tests:

```bash
cd gatling
mvn gatling:test
```

Or to run from the root directory:

```bash
mvn -f gatling/pom.xml gatling:test
```

## Docker

The load tests can also be run via Docker using the `gatling` stage:

```bash
docker build --target gatling -t jasper-gatling .
docker run jasper-gatling
```

## Configuration

- **Gatling Version**: 3.14.4
- **Target URL**: http://localhost:8081 (configurable in simulation files)
- **Simulations**: Located in `src/test/java/simulations/jasper/`

## Adding New Tests

Create new simulation classes in `src/test/java/simulations/jasper/` that extend `Simulation` from Gatling.

## Dependencies

This module uses its own dependencies isolated from the main application:
- Gatling Highcharts 3.14.4
- Netty 4.2.6.Final (via Gatling)

This isolation prevents dependency conflicts with the main Spring Boot application.