package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class SimpleJasperSimulation extends Simulation {

	HttpProtocolBuilder httpProtocol = http
		.baseUrl("http://localhost:8080") // Change as needed
		.acceptHeader("application/json")
		.userAgentHeader("Gatling Load Test");

	ChainBuilder getAllRefs = exec(
		http("Get All Refs")
			.get("/api/ref")
			.check(status().is(200))
	).pause(1);

	ChainBuilder getOneRef = exec(
		http("Get Single Ref")
			.get("/api/ref")
			.queryParam("url", "https://example.com")
			.check(status().is(200))
	).pause(1);

	ScenarioBuilder scn = scenario("Simple Jasper Load Test")
		.exec(
			repeat(2).on(
				exec(getAllRefs)
					.exec(getOneRef)
			)
		);

	{
		setUp(
			scn.injectOpen(
				nothingFor(1),
				atOnceUsers(1)
			)
		).protocols(httpProtocol);
	}
}
