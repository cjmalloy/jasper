package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Step load with short think times to find maximum throughput.
 * The run stops once more than LT_MAX_ERROR_PERCENT (default 5%) of recent requests fail,
 * so the last step reached and its throughput are the capacity, not the rejection rate.
 */
public class CapacitySimulation extends Simulation {
	Workload workload = new Workload(false, envDouble("LT_THINK", 0.1), envDouble("LT_MAX_ERROR_PERCENT", 5)).stopGracefully();
	double step = envDouble("LT_CAPACITY_STEP", 4);
	int levels = envInt("LT_CAPACITY_LEVELS", 10);
	Duration level = envSeconds("LT_CAPACITY_LEVEL_SECONDS", 20);

	{
		setUp(
			workload.browse("User", "USER", 5, 10).injectOpen(
				incrementUsersPerSec(step * 0.95)
					.times(levels)
					.eachLevelLasting(level)
					.separatedByRampsLasting(Duration.ofSeconds(5))
					.startingFrom(step * 0.95)),
			workload.browse("Moderator", "MOD", 5, 10).injectOpen(
				constantUsersPerSec(step * 0.05).during(level.plusSeconds(5).multipliedBy(levels)))
		).protocols(workload.protocol("Capacity"))
			.maxDuration(level.plusSeconds(5).multipliedBy(levels).plusSeconds(30));
	}
}
