package simulations.jasper;

import io.gatling.javaapi.core.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Functional check: one user per role walks every request type once, and every request must succeed.
 * The path is deterministic so every request type is always sent.
 */
public class SmokeTestSimulation extends Simulation {
	Workload workload = new Workload(false, 0.1, 100);

	ScenarioBuilder user = scenario("Smoke User")
		.exec(workload.login("USER"))
		.exec(session -> session.set("ws", true))
		.exec(workload.appLoad())
		.exec(workload.connectWebsocket())
		.exec(workload.subscriptionFeed())
		.exec(workload.tagFeed())
		.exec(workload.nextPage())
		.exec(workload.userFeed())
		.exec(workload.search())
		.exec(workload.allFeed())
		.exec(crashLoadGeneratorIf("All Feed returned no Refs, run the Seed simulation first",
			session -> session.getList("pageUrls").isEmpty()))
		.exec(workload.getRef())
		.exec(workload.vote())
		.exec(workload.comment())
		.exec(workload.responses())
		.exec(workload.graph())
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
