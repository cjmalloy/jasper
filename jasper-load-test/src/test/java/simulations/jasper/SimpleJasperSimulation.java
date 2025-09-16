package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class SimpleJasperSimulation extends Simulation {

	HttpProtocolBuilder httpProtocol = http
		.baseUrl("http://localhost:8081")
		.acceptHeader("application/json")
		.userAgentHeader("Gatling Load Test");

	ChainBuilder getAllRefs = exec(
		http("Get All Refs")
			.get("/api/v1/ref/page")
			.check(status().is(200))
	).pause(1);

	ChainBuilder createOneRef = exec(
		http("Create Single Ref")
			.post("/api/v1/ref")
			.body(StringBody("""
				{
					"url": "https://example.com",
					"title": "Example Title",
					"tags": ["test", "gatling"]
				}"""))
			.check(status().is(201))
	).pause(1);

	ChainBuilder getOneRef = exec(
		http("Get Single Ref")
			.get("/api/v1/ref")
			.queryParam("url", "https://example.com")
			.check(status().is(200))
	).pause(1);

	ScenarioBuilder scn = scenario("Simple Jasper Load Test")
		.exec(createOneRef)
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
