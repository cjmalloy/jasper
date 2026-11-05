package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Multi-origin Inferno with a per-origin rate limit: a gradient of traffic across many origins,
 * so only a few go extreme while the quiet ones must stay healthy.
 *
 * - Origins: the load test origin (LT_ORIGIN, default @loadtest) plus tenant sub-origins
 *   @loadtest.t1 ... (LT_RATE_LIMIT_ORIGINS in total, default 6). Tenants are seeded with
 *   LT_TENANT_REFS Refs each (default 500) and deleted at the end.
 * - Sets _config/security in the load test origin to LT_RATE_LIMIT requests per second. Sub-origins inherit it,
 *   and every origin gets its own limiter, so each tenant can use up to LT_RATE_LIMIT requests per second.
 *   It also caps each origin at LT_RATE_LIMIT_CONCURRENT requests in flight (default 4, 0 for unlimited),
 *   so a loud origin cannot take every worker and quiet origins always find a free slot.
 * - Every origin has the same shape as Inferno: a 1 session/s trickle, a burst at 15s, and a burst of a third
 *   of that at 85s. Bursts shrink by LT_RATE_LIMIT_GRADIENT (default 4) per origin: 3,000, 750, 188, 47, 12, 3
 *   sessions by default.
 * - Deletes the tenants and the _config/security template again.
 *
 * Origins whose bursts stay well below the limit (about 3 requests per session over 10s, at most half of
 * LT_RATE_LIMIT) are asserted to stay below 5% errors and a p95 of 5s. The quietest origin is always asserted.
 * The loud origins get 429s: compare how much of their traffic still succeeds with Inferno.
 * Never run against production.
 */
public class RateLimitSimulation extends Simulation {
	static final double REQUESTS_PER_SESSION = 3;
	static final Duration BURST = Duration.ofSeconds(10);

	Workload admin = new Workload(false, 0, 100);
	int burstSessions = envInt("LT_INFERNO_BURST", 3_000);
	int rateLimit = envInt("LT_RATE_LIMIT", 50);
	int origins = Math.max(2, envInt("LT_RATE_LIMIT_ORIGINS", 6));
	int concurrentLimit = envInt("LT_RATE_LIMIT_CONCURRENT", 4);
	double gradient = envDouble("LT_RATE_LIMIT_GRADIENT", 4);
	int tenantRefs = envInt("LT_TENANT_REFS", 500);
	String securityConfig = "_config/security" + admin.origin;

	List<String> originNames = IntStream.range(0, origins)
		.mapToObj(i -> i == 0 ? admin.origin : admin.origin + ".t" + i)
		.toList();

	int burst(int i) {
		return (int) Math.max(1, Math.round(burstSessions / Math.pow(gradient, i)));
	}

	boolean quiet(int i) {
		return i == origins - 1 || burst(i) * REQUESTS_PER_SESSION / BURST.toSeconds() <= rateLimit / 2.0;
	}

	HttpRequestActionBuilder deleteConfig(String name) {
		return http(name)
			.delete("/api/v1/template")
			.queryParam("tag", securityConfig)
			.check(status().in(204, 404));
	}

	ScenarioBuilder setup = scenario("Set Rate Limit")
		.exec(admin.login("ADMIN"))
		// Left over from an interrupted run
		.exec(admin.req(deleteConfig("Clear Rate Limit")))
		.exec(admin.req(http("Set Rate Limit")
			.post("/api/v1/template")
			.body(StringBody("{\"tag\":\"_config/security\",\"origin\":" + Dataset.quote(admin.origin) +
				",\"config\":{\"maxRequests\":" + rateLimit + ",\"maxRequestsPeriod\":\"PT1S\",\"maxConcurrentRequests\":" + concurrentLimit + "}}"))
			.check(status().is(201))))
		// The rate limiters are replaced when the template update is received
		.pause(Duration.ofSeconds(2));

	ScenarioBuilder teardown = scenario("Remove Rate Limit")
		.exec(admin.login("ADMIN"))
		.foreach(originNames.subList(1, origins), "tenant").on(
			exec(admin.req(http("Delete Tenant")
				.delete("/api/v1/origin")
				.queryParam("origin", "#{tenant}")
				.check(status().is(204)))))
		.exec(admin.req(deleteConfig("Remove Rate Limit")));

	ScenarioBuilder traffic(int i) {
		var origin = originNames.get(i);
		var workload = new Workload(false, origin, 0, 100);
		return scenario("Origin " + origin)
			.exec(workload.login("USER"))
			.group(origin).on(
				exec(workload.homeFeed())
					.randomSwitch().on(
						percent(40.0).then(workload.tagFeed()),
						percent(35.0).then(workload.openRef()),
						percent(25.0).then(workload.submitLink())));
	}

	{
		var tenants = new ArrayList<PopulationBuilder>();
		for (var i = 1; i < origins; i++) {
			var origin = originNames.get(i);
			tenants.add(scenario("Seed " + origin)
				.exec(SeedSimulation.seed(origin, tenantRefs))
				.injectOpen(atOnceUsers(1))
				.protocols(new Workload(false, origin, 0, 100).protocol("Rate Limit Seed")));
		}
		var load = new ArrayList<PopulationBuilder>();
		var assertions = new ArrayList<Assertion>();
		for (var i = 0; i < origins; i++) {
			var origin = originNames.get(i);
			System.out.printf("RateLimit: %s bursts of %d and %d sessions%s%n", origin, burst(i), burst(i) / 3, quiet(i) ? " (asserted)" : "");
			load.add(traffic(i).injectOpen(
					constantUsersPerSec(1).during(Duration.ofSeconds(15)),
					rampUsers(burst(i)).during(BURST),
					constantUsersPerSec(1).during(Duration.ofSeconds(60)),
					rampUsers(burst(i) / 3).during(BURST),
					constantUsersPerSec(1).during(Duration.ofSeconds(60)))
				.protocols(new Workload(false, origin, 0, 100).protocol("Rate Limit " + origin)));
			if (quiet(i)) {
				assertions.add(details(origin).failedRequests().percent().lt(5.0));
				assertions.add(details(origin).responseTime().percentile(95.0).lt(5000));
			}
		}
		assertions.add(details("Set Rate Limit").failedRequests().count().is(0L));
		assertions.add(details("Remove Rate Limit").failedRequests().count().is(0L));
		setUp(
			setup.injectOpen(atOnceUsers(1)).protocols(admin.protocol("Rate Limit"))
				.andThen(tenants)
				.andThen(load)
				.andThen(teardown.injectOpen(atOnceUsers(1)).protocols(admin.protocol("Rate Limit")))
		)
			.maxDuration(Duration.ofMinutes(6))
			.assertions(assertions);
	}
}
