package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

import static io.gatling.javaapi.core.CoreDsl.*;
import static simulations.jasper.LoadConfig.*;

/**
 * Step load with short think times to find maximum throughput.
 * The run stops once more than LT_MAX_ERROR_PERCENT (default 5%) of recent requests fail.
 * The capacity is the rate of the last step completed before that, written to
 * target/gatling/Capacity.capacity.json for the CI summary. Mean throughput over the run
 * depends on where it stopped, so compare the capacity instead.
 */
public class CapacitySimulation extends Simulation {
	Workload workload = new Workload(false, envDouble("LT_THINK", 0.1), envDouble("LT_MAX_ERROR_PERCENT", 5)).stopGracefully();
	double step = envDouble("LT_CAPACITY_STEP", 4);
	int levels = envInt("LT_CAPACITY_LEVELS", 10);
	Duration level = envSeconds("LT_CAPACITY_LEVEL_SECONDS", 20);
	Duration ramp = Duration.ofSeconds(5);
	long start;

	{
		setUp(
			workload.browse("User", "USER", 5, 10).injectOpen(
				incrementUsersPerSec(step * 0.95)
					.times(levels)
					.eachLevelLasting(level)
					.separatedByRampsLasting(ramp)
					.startingFrom(step * 0.95)),
			workload.browse("Moderator", "MOD", 5, 10).injectOpen(
				constantUsersPerSec(step * 0.05).during(level.plus(ramp).multipliedBy(levels)))
		).protocols(workload.protocol("Capacity"))
			.maxDuration(level.plus(ramp).multipliedBy(levels).plusSeconds(30));
	}

	@Override
	public void before() {
		start = System.currentTimeMillis();
	}

	@Override
	public void after() {
		var stoppedAt = workload.stoppedAt.get();
		// Levels completed before the error stop; each level is followed by a ramp to the next,
		// so a stop during a ramp still counts the level before it
		var completed = stoppedAt == 0
			? levels
			: (int) Math.min(levels, (stoppedAt - start + ramp.toMillis()) / level.plus(ramp).toMillis());
		var sessions = completed == 0 ? 0 : step * (0.95 * completed + 0.05);
		var json = String.format(Locale.ROOT,
			"{\"sessionsPerSec\": %.2f, \"levelsCompleted\": %d, \"levels\": %d, \"stopped\": %b}%n",
			sessions, completed, levels, stoppedAt != 0);
		System.out.print("Capacity: " + json);
		try {
			var dir = Path.of(env("LT_RESULTS_DIR", "target/gatling"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("Capacity.capacity.json"), json);
		} catch (IOException e) {
			System.err.println("Could not write capacity result: " + e);
		}
	}
}
