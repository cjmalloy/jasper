package simulations.jasper;

import io.gatling.javaapi.core.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Delete everything the load test wrote by deleting its sub-origin (LT_ORIGIN, default @loadtest).
 */
public class CleanupSimulation extends Simulation {
	Workload workload = new Workload(false, 1, 100);

	ScenarioBuilder cleanup = scenario("Cleanup")
		.exec(session -> session.set("auth", authorization(Dataset.userName("ADMIN", 0), "ADMIN")))
		.exec(Workload.startCsrf())
		.exec(http("Delete Load Test Origin")
			.delete("/api/v1/origin")
			.queryParam("origin", workload.origin)
			.check(status().is(204)));

	{
		setUp(cleanup.injectOpen(atOnceUsers(1)))
			.protocols(workload.protocol("Cleanup"))
			.assertions(global().failedRequests().count().is(0L));
	}
}
