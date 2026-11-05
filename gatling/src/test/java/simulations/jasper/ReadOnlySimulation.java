package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Production profile: the Regression browsing mix without any writes, proxy or scrape requests.
 *
 * Reads the default origin unless LT_ORIGIN is set. Conservative default rate, and the run is
 * aborted when more than LT_MAX_ERROR_PERCENT (default 2%) of recent requests fail.
 */
public class ReadOnlySimulation extends Simulation {
	Workload workload = new Workload(true, THINK, envDouble("LT_MAX_ERROR_PERCENT", 2));
	double rate = envDouble("LT_RATE", 0.2);
	Duration ramp = Duration.ofSeconds(60);
	Duration duration = envSeconds("LT_DURATION", 300);

	{
		setUp(
			workload.browse("Reader", "USER", 5, 15).injectOpen(
				rampUsersPerSec(0).to(rate).during(ramp),
				constantUsersPerSec(rate).during(duration))
		).protocols(workload.protocol("Read Only"))
			.maxDuration(ramp.plus(duration).plusMinutes(1))
			.assertions(
				global().failedRequests().percent().lt(1.0),
				global().responseTime().percentile(95.0).lt(2000)
			);
	}
}
