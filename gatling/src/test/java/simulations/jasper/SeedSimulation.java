package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Fill the load test origin with a generated knowledge base before measuring.
 *
 * Skipped when the origin already holds LT_SEED_REFS Refs, so it is safe to run before every simulation.
 * Pushes in batches through the replication API, which runs the same ingest and metadata generation as normal writes.
 */
public class SeedSimulation extends Simulation {
	static final int BATCH = 250;

	Workload workload = new Workload(false, 1, 100);
	String origin = workload.origin;
	int total = envInt("LT_SEED_REFS", 5000);

	ScenarioBuilder seed = scenario("Seed")
		.exec(session -> session.set("auth", authorization(Dataset.userName("ADMIN", 0), "ADMIN")))
		.exec(Workload.startCsrf())
		.exec(http("Count Seeded Refs")
			.get("/api/v1/ref/count")
			.queryParam("query", origin)
			.check(bodyString().transform(Long::parseLong).saveAs("existing")))
		.exitHereIfFailed()
		.doIfOrElse(session -> session.getLong("existing") >= total).then(
			exec(session -> {
				System.out.println(origin + " already has " + session.getLong("existing") + " Refs, skipping seed");
				return session;
			})
		).orElse(
			exec(Workload.csrf())
				.exec(http("Install Comment Plugin")
				.post("/api/v1/plugin")
				.body(StringBody("{\"tag\":\"plugin/comment\",\"origin\":\"" + origin + "\",\"name\":\"Comment\"}"))
				// Idempotent: the plugin may remain from a partial seed
				.check(status().in(201, 409)))
				.exec(Workload.csrf())
				.exec(http("Install Template")
					.post("/api/v1/template")
					.body(StringBody("{\"tag\":\"kb\",\"origin\":\"" + origin + "\",\"name\":\"Knowledge Base\"}"))
					.check(status().in(201, 409)))
				.exec(http("Push Exts")
					.post("/pub/api/v1/repl/ext")
					.queryParam("origin", origin)
					.body(StringBody(Dataset.exts(TOPICS))))
				.repeat((total + BATCH - 1) / BATCH, "batch").on(
					exec(http("Push Refs")
						.post("/pub/api/v1/repl/ref")
						.queryParam("origin", origin)
						.body(StringBody(session -> {
							var from = session.getInt("batch") * BATCH;
							return Dataset.refs(from, Math.min(total, from + BATCH), total, TOPICS);
						}))
						.requestTimeout(Duration.ofMinutes(5)))
						.exitHereIfFailed())
		);

	{
		setUp(seed.injectOpen(atOnceUsers(1)))
			.protocols(workload.protocol("Seed"))
			.assertions(global().failedRequests().count().is(0L));
	}
}
