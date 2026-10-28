package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Robustness test, not a performance signal: two bursts well beyond capacity,
 * then check the server recovers.
 *
 * - 0-15s warmup trickle
 * - 15-25s burst of LT_INFERNO_BURST (default 3,000) sessions, then trickle
 * - 85-95s burst of a third of that, then trickle
 * - Recovery probes start 30s after each burst and must succeed
 *
 * Bursts are sized to overload the server, not the load generator or Docker's port proxy:
 * tens of thousands of sessions in a few seconds exhaust client connections first, and
 * every request then fails with connection errors no matter how the server behaves.
 * Burst requests are expected to fail (429/503) and are not asserted on.
 */
public class InfernoSimulation extends Simulation {
	Workload workload = new Workload(false, 0, 100);
	int burstSessions = envInt("LT_INFERNO_BURST", 3_000);

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
				rampUsers(burstSessions).during(Duration.ofSeconds(10)),
				constantUsersPerSec(5).during(Duration.ofSeconds(60)),
				rampUsers(burstSessions / 3).during(Duration.ofSeconds(10)),
				constantUsersPerSec(5).during(Duration.ofSeconds(60))),
			recovery.injectOpen(
				nothingFor(Duration.ofSeconds(55)),
				constantUsersPerSec(2).during(Duration.ofSeconds(30)),
				nothingFor(Duration.ofSeconds(40)),
				constantUsersPerSec(2).during(Duration.ofSeconds(30)))
		).protocols(workload.protocol("Inferno"))
			.maxDuration(Duration.ofMinutes(3))
			.assertions(
				details("Recovery").failedRequests().percent().lt(5.0),
				details("Recovery").responseTime().percentile(95.0).lt(5000)
			);
	}
}
