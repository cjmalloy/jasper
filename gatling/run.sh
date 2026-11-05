#!/bin/bash
# Seed the server (idempotent), run one simulation, and copy its report to ./report.
# Exits with Gatling's exit code, so assertion failures fail the run.
#
# GATLING_TEST: simulation name without the Simulation suffix (default SmokeTest)
# LT_SKIP_SEED: set to true to skip seeding (always skipped for ReadOnly and Cleanup)
set -u
TEST="${GATLING_TEST:-SmokeTest}"
MVN="${MVN:-mvn}"
REPORT="${REPORT_DIR:-report}"
mkdir -p "$REPORT"
# Containers are reused between runs: only copy this run's report
rm -rf target/gatling

if [[ "${LT_SKIP_SEED:-false}" != "true" && "$TEST" != "ReadOnly" && "$TEST" != "Cleanup" ]]; then
  $MVN -B gatling:test -Dgatling.simulationClass=simulations.jasper.SeedSimulation -Dgatling.noReports=true
  rc=$?
  rm -rf target/gatling/seedsimulation-*
  if [[ $rc -ne 0 ]]; then
    echo "Seeding failed with exit code $rc"
    echo "$rc" > "$REPORT/$TEST.status"
    exit $rc
  fi
fi

$MVN -B gatling:test -Dgatling.simulationClass="simulations.jasper.${TEST}Simulation"
rc=$?
cp -r target/gatling/* "$REPORT/" 2>/dev/null
echo "$rc" > "$REPORT/$TEST.status"
exit $rc
