package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Inferno with a per-origin rate limit: the same bursts from the load test origin, while
 * another origin (the default origin, read only) keeps browsing.
 *
 * - Sets _config/security in the load test origin to LT_RATE_LIMIT requests per second
 * - 0-15s warmup trickle
 * - 15-25s burst of LT_INFERNO_BURST (default 3,000) sessions, then trickle
 * - 85-95s burst of a third of that, then trickle
 * - Neighbor sessions in the default origin browse throughout, including during the bursts
 * - Deletes the _config/security template again
 *
 * Without a rate limit the bursts fill the concurrent request bulkhead and everyone gets 503
 * (see Inferno). With it, excess requests from the noisy origin get a cheap 429 instead,
 * the work that is accepted completes quickly, and neighbors must stay below 5% errors.
 * Compare the successful throughput with Inferno. Never run against production.
 */
public class RateLimitSimulation extends Simulation {
	Workload workload = new Workload(false, 0, 100);
	Workload neighbors = new Workload(true, 0.2, 100);
	int burstSessions = envInt("LT_INFERNO_BURST", 3_000);
	int rateLimit = envInt("LT_RATE_LIMIT", 150);
	String securityConfig = "_config/security" + workload.origin;

	HttpRequestActionBuilder deleteConfig(String name) {
		return http(name)
			.delete("/api/v1/template")
			.queryParam("tag", securityConfig)
			.check(status().in(204, 404));
	}

	ScenarioBuilder setup = scenario("Set Rate Limit")
		.exec(workload.login("ADMIN"))
		// Left over from an interrupted run
		.exec(workload.req(deleteConfig("Clear Rate Limit")))
		.exec(workload.req(http("Set Rate Limit")
			.post("/api/v1/template")
			.body(StringBody("{\"tag\":\"_config/security\",\"origin\":" + Dataset.quote(workload.origin) +
				",\"config\":{\"maxRequests\":" + rateLimit + ",\"maxRequestsPeriod\":\"PT1S\"}}"))
			.check(status().is(201))))
		// The rate limiter is replaced when the template update is received
		.pause(Duration.ofSeconds(2));

	ScenarioBuilder burst = scenario("Rate Limited Burst")
		.exec(workload.login("USER"))
		.exec(workload.homeFeed())
		.randomSwitch().on(
			percent(40.0).then(workload.tagFeed()),
			percent(35.0).then(workload.openRef()),
			percent(25.0).then(workload.submitLink()));

	ScenarioBuilder neighbor = scenario("Neighbor")
		.exec(neighbors.login("USER"))
		.group("Neighbor").on(
			exec(neighbors.homeFeed())
				.exec(neighbors.think(1, 3))
				.exec(neighbors.openRef())
				.exec(neighbors.think(1, 3))
				.exec(neighbors.tagFeed()));

	ScenarioBuilder teardown = scenario("Remove Rate Limit")
		.exec(workload.login("ADMIN"))
		.exec(workload.req(deleteConfig("Remove Rate Limit")));

	{
		if (neighbors.origin.equals(workload.origin)) {
			throw new IllegalArgumentException("RateLimit needs the neighbors in a different origin than LT_ORIGIN");
		}
		setUp(
			setup.injectOpen(atOnceUsers(1)).protocols(workload.protocol("Rate Limit"))
				.andThen(
					burst.injectOpen(
						constantUsersPerSec(5).during(Duration.ofSeconds(15)),
						rampUsers(burstSessions).during(Duration.ofSeconds(10)),
						constantUsersPerSec(5).during(Duration.ofSeconds(60)),
						rampUsers(burstSessions / 3).during(Duration.ofSeconds(10)),
						constantUsersPerSec(5).during(Duration.ofSeconds(60))
					).protocols(workload.protocol("Rate Limit")),
					neighbor.injectOpen(
						constantUsersPerSec(2).during(Duration.ofSeconds(160))
					).protocols(neighbors.protocol("Rate Limit Neighbor")))
				.andThen(teardown.injectOpen(atOnceUsers(1)).protocols(workload.protocol("Rate Limit")))
		)
			.maxDuration(Duration.ofMinutes(4))
			.assertions(
				details("Neighbor").failedRequests().percent().lt(5.0),
				details("Neighbor").responseTime().percentile(95.0).lt(5000),
				details("Set Rate Limit").failedRequests().count().is(0L),
				details("Remove Rate Limit").failedRequests().count().is(0L)
			);
	}
}
