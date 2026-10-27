package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Robustness test, not a performance signal: two bursts far beyond capacity,
 * then check the server recovers.
 *
 * - 0-15s warmup trickle
 * - 15-25s burst of 20,000 sessions, then trickle
 * - 75-85s burst of 5,000 sessions, then trickle
 * - Recovery probes start 20s after each burst and must succeed
 *
 * Burst requests are expected to fail (429/503) and are not asserted on.
 */
public class InfernoSimulation extends Simulation {
	Workload workload = new Workload(false, 0, 100);

	ScenarioBuilder burst = scenario("Inferno Burst")
		.exec(workload.login("USER"))
		.exec(workload.homeFeed())
		.randomSwitch().on(
			percent(40.0).then(workload.tagFeed()),
			percent(35.0).then(workload.openRef()),
			percent(25.0).then(workload.submitLink()));

	ScenarioBuilder recovery = scenario("Recovery Probe")
		.exec(workload.login("USER"))
		.group("Recovery").on(
			exec(workload.homeFeed())
				.exec(workload.openRef()));

	{
		setUp(
			burst.injectOpen(
				constantUsersPerSec(5).during(Duration.ofSeconds(15)),
				rampUsers(20_000).during(Duration.ofSeconds(10)),
				constantUsersPerSec(5).during(Duration.ofSeconds(50)),
				rampUsers(5_000).during(Duration.ofSeconds(10)),
				constantUsersPerSec(5).during(Duration.ofSeconds(50))),
			recovery.injectOpen(
				nothingFor(Duration.ofSeconds(45)),
				constantUsersPerSec(2).during(Duration.ofSeconds(30)),
				nothingFor(Duration.ofSeconds(30)),
				constantUsersPerSec(2).during(Duration.ofSeconds(30)))
		).protocols(workload.protocol("Inferno"))
			.maxDuration(Duration.ofMinutes(3))
			.assertions(
				details("Recovery").failedRequests().percent().lt(5.0),
				details("Recovery").responseTime().percentile(95.0).lt(5000)
			);
	}
}
