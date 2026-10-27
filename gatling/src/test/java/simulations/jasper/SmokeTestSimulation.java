package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Functional check: one user per role walks every request type once, and every request must succeed.
 */
public class SmokeTestSimulation extends Simulation {
	Workload workload = new Workload(false, 0.1, 100);

	ScenarioBuilder user = scenario("Smoke User")
		.exec(workload.login("USER"))
		.exec(session -> session.set("ws", true))
		.exec(workload.appLoad())
		.exec(workload.connectWebsocket())
		.exec(workload.homeFeed())
		.exec(workload.tagFeed())
		.exec(workload.nextPage())
		.exec(workload.userFeed())
		.exec(workload.search())
		.exec(workload.tagFeed())
		.exec(workload.openRef())
		.exec(workload.vote())
		.exec(workload.comment())
		.exec(workload.submitLink())
		.exec(workload.tagOwnRef())
		.exec(workload.editOwnRef())
		.exec(workload.think(1, 2))
		.exec(workload.closeWebsocket());

	ScenarioBuilder mod = scenario("Smoke Moderator")
		.exec(workload.login("MOD"))
		.exec(workload.appLoad())
		.exec(workload.editExt());

	ScenarioBuilder admin = scenario("Smoke Admin")
		.exec(workload.login("ADMIN"))
		.exec(workload.appLoad())
		.exec(workload.editTemplate());

	{
		setUp(
			user.injectOpen(atOnceUsers(3)),
			mod.injectOpen(atOnceUsers(1)),
			admin.injectOpen(atOnceUsers(1))
		).protocols(workload.protocol("Smoke Test"))
			.maxDuration(Duration.ofMinutes(2))
			.assertions(global().failedRequests().count().is(0L));
	}
}
