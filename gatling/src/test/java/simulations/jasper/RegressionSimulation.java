package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Fixed-rate realistic workload that should not saturate the server.
 * Compare runs against a baseline to catch regressions.
 *
 * LT_RATE sessions start per second (90% users, 8% moderators, 2% admins), for LT_DURATION seconds.
 */
public class RegressionSimulation extends Simulation {
	Workload workload = new Workload(false, THINK, envDouble("LT_MAX_ERROR_PERCENT", 10));
	double rate = envDouble("LT_RATE", 1.0);
	Duration ramp = Duration.ofSeconds(30);
	Duration duration = envSeconds("LT_DURATION", 180);

	{
		setUp(
			workload.browse("User", "USER", 5, 15).injectOpen(
				rampUsersPerSec(0).to(rate * 0.9).during(ramp),
				constantUsersPerSec(rate * 0.9).during(duration)),
			workload.browse("Moderator", "MOD", 5, 15).injectOpen(
				constantUsersPerSec(rate * 0.08).during(ramp.plus(duration))),
			workload.browse("Admin", "ADMIN", 5, 15).injectOpen(
				constantUsersPerSec(rate * 0.02).during(ramp.plus(duration)))
		).protocols(workload.protocol("Regression"))
			.maxDuration(ramp.plus(duration).plusMinutes(1))
			.assertions(
				global().failedRequests().percent().lt(1.0),
				global().responseTime().percentile(95.0).lt(2000)
			);
	}
}
