package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;

/**
 * Stress Test and Edge Cases for Jasper
 *
 * This simulation tests system limits and edge cases:
 * - High-volume concurrent operations
 * - Large payloads and complex queries
 * - Error conditions and recovery
 * - Replication and backup scenarios
 * - System administration operations
 */
public class StressTestSimulation extends Simulation {

	HttpProtocolBuilder httpProtocol = http
		.baseUrl("http://localhost:8081")
		.acceptHeader("application/json")
		.contentTypeHeader("application/json")
		.userAgentHeader("Gatling Stress Test")
		.disableFollowRedirect()
		.check(status().not(500)); // Allow other error codes for stress testing

	// ====================== CSRF Token Setup ======================

	ChainBuilder fetchCsrfToken = exec(
		http("Fetch CSRF Token")
			.get("/api/v1/ref/page")
			.queryParam("size", "1")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").saveAs("csrfToken"))
	);

	// ====================== High Volume Operations ======================

	ChainBuilder rapidRefCreation =
		exec(session -> {
			// Add timestamp to URL for uniqueness to avoid duplicate key violations
			String url = "https://stress-test.example.com/" +
				System.currentTimeMillis() + "-" +
				(1 + new java.util.Random().nextInt(100000));
			return session.set("rapidRefUrl", url);
		})
			.exec(
				http("Rapid Ref Creation")
					.post("/api/v1/ref")
					.header("X-XSRF-TOKEN", "#{csrfToken}")
					.body(StringBody("""
				{
					"url": "#{rapidRefUrl}",
					"title": "Stress Test Reference #{randomInt(1,100000)} - Load#{randomInt(10000,99999)}",
					"comment": "StressTest#{randomInt(100000,999999)} - This is a stress test reference with substantial content to test system limits and performance under high load conditions. Content#{randomInt(100000,999999)}",
					"tags": [
						"stresstest",
						"performance",
						"load.#{randomInt(1,1000)}",
						"batch.#{randomInt(1,100)}",
						"category.#{randomInt(1,50)}",
						"priority.#{randomInt(1,10)}"
					],
					"sources": [
						"https://source1.example.com/#{randomInt(1,1000)}",
						"https://source2.example.com/#{randomInt(1,1000)}",
						"https://source3.example.com/#{randomInt(1,1000)}"
					]
				}"""))
					.check(status().in(201, 409, 400))
					.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
			).pause(Duration.ofMillis(50), Duration.ofMillis(200));

	ChainBuilder largePageQuery = exec(
		http("Large Page Query")
			.get("/api/v1/ref/page")
			.queryParam("size", "100")
			.queryParam("query", "stress-test OR performance OR load-#{randomInt(1,100)}")
			.queryParam("sort", "modified,desc")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
			.check(responseTimeInMillis().lt(5000))
	).pause(Duration.ofMillis(100), Duration.ofMillis(500));

	ChainBuilder complexSearchQuery = exec(
		http("Complex Search Query")
			.get("/api/v1/ref/page")
			.queryParam("query", "(stresstest:performance)|load.#{randomInt(1,100)}:!excluded")
			.queryParam("search", "stress test performance load content#{randomInt(1,100)}")
			.queryParam("modifiedAfter", "2024-01-01T00:00:00Z")
			.queryParam("size", "50")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
	).pause(Duration.ofMillis(200), Duration.ofMillis(800));

	// ====================== Large Payload Operations ======================

	ChainBuilder createLargeExt = exec(session -> {
		// Build large config JSON
		StringBuilder largeConfig = new StringBuilder("{\"type\":\"large-test\",\"data\":[");
		for (int i = 0; i < 100; i++) {
			if (i > 0) largeConfig.append(",");
			largeConfig.append("{\"id\":").append(i)
				.append(",\"name\":\"item").append(i).append("_").append(System.currentTimeMillis() % 10000)
				.append("\",\"description\":\"desc").append(i).append("_").append(System.currentTimeMillis() % 100000)
				.append("\",\"value\":").append(Math.random() * 1000).append("}");
		}
		largeConfig.append("]}");

		int randomId = (int)(Math.random() * 10000) + 1;
		int randomNum = (int)(Math.random() * 1000) + 1;
		
		String body = String.format("""
			{
				"tag": "+ext/large.test.%d",
				"name": "Large Test Extension %d",
				"config": %s
			}""",
			randomId,
			randomNum,
			largeConfig.toString());
		
		return session.set("largeExtBody", body);
	})
	.exec(
		http("Create Large Extension")
			.post("/api/v1/ext")
			.header("X-XSRF-TOKEN", "#{csrfToken}")
			.body(StringBody("#{largeExtBody}"))
			.check(status().in(201, 409))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
	).pause(Duration.ofMillis(300), Duration.ofMillis(1000));

	// ====================== Error Condition Testing ======================

	ChainBuilder testNotFoundErrors = exec(
		http("Test Not Found - Random URL")
			.get("/api/v1/ref")
			.queryParam("url", "https://nonexistent-#{randomInt(1,100000)}.example.com/#{randomUuid()}")
			.check(status().is(404))
	).pause(Duration.ofMillis(100), Duration.ofMillis(300));

	ChainBuilder testInvalidData = exec(
		http("Test Invalid Data")
			.post("/api/v1/ref")
			.header("X-XSRF-TOKEN", "#{csrfToken}")
			.body(StringBody("""
				{
					"url": "invalid-url-test#{randomInt(1,1000)}",
					"title": "",
					"tags": ["invalidtag#{randomInt(100000,999999)}"]
				}"""))
			.check(status().in(400, 422))
	).pause(Duration.ofMillis(100), Duration.ofMillis(300));

	ChainBuilder testMalformedJson = exec(
		http("Test Malformed JSON")
			.post("/api/v1/ref")
			.header("X-XSRF-TOKEN", "#{csrfToken}")
			.body(StringBody("""
				{
					"url": "https://test.com/malformed",
					"title": "Test"
					"invalid": "json",
				}"""))
			.check(status().is(400))
	).pause(Duration.ofMillis(100), Duration.ofMillis(300));

	// ====================== Replication Testing ======================

	ChainBuilder testReplicationEndpoints = exec(
		http("Test Replication - Get Refs")
			.get("/pub/api/v1/repl/ref")
			.queryParam("size", "100")
			.queryParam("modifiedAfter", "2024-01-01T00:00:00Z")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
	).pause(Duration.ofMillis(200), Duration.ofMillis(500))
		.exec(
			http("Test Replication - Get Cursor")
				.get("/pub/api/v1/repl/ref/cursor")
				.check(status().is(200))
				.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
		).pause(Duration.ofMillis(100), Duration.ofMillis(300))
		.exec(
			http("Test Replication - Get Extensions")
				.get("/pub/api/v1/repl/ext")
				.queryParam("size", "50")
				.check(status().is(200))
				.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
		);

	// ====================== Backup Operations ======================

	ChainBuilder testBackupOperations = exec(
		http("Create Backup")
			.post("/api/v1/backup")
			.header("X-XSRF-TOKEN", "#{csrfToken}")
			.body(StringBody("""
				{
					"includeRefs": true,
					"includeExts": true,
					"includeUsers": false,
					"maxItems": 1000
				}"""))
			.check(status().is(200))  // BackupController returns 200, not 201 (missing @ResponseStatus annotation)
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
	).pause(Duration.ofSeconds(2), Duration.ofSeconds(5))
		.exec(
			http("List Backups")
				.get("/api/v1/backup")
				.check(status().is(200))
				.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
		);

	// ====================== System Administration ======================

	ChainBuilder testAdminOperations = exec(
		http("Get User Info")
			.get("/api/v1/user/whoami")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
	).pause(Duration.ofMillis(200), Duration.ofMillis(500))
		.exec(
			http("Browse All Users")
				.get("/api/v1/user/page")
				.queryParam("size", "50")
				.check(status().is(200))
				.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
		);

	// ====================== Content Enrichment Stress ======================

	ChainBuilder stressProxyOperations = exec(
		http("Stress Proxy Operation")
			.get("/api/v1/proxy")
			.queryParam("url", "https://httpbin.org/delay/#{randomInt(1,3)}")
			.check(status().in(200, 404, 408, 503))
			.check(responseTimeInMillis().lt(10000))
	).pause(Duration.ofMillis(500), Duration.ofMillis(2000));

	ChainBuilder stressScrapeOperations = exec(
		http("Stress Web Scraping")
			.get("/api/v1/scrape/web")
			.queryParam("url", "https://httpbin.org/html")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
	).pause(Duration.ofMillis(1000), Duration.ofMillis(3000));

	// ====================== Concurrent Update Stress ======================

	ChainBuilder concurrentUpdates =
		exec(session -> {
			// Use timestamp + random to make URLs unique per virtual user to avoid duplicate key violations
			String url = "https://stress-test.example.com/shared-" +
				System.currentTimeMillis() + "-" +
				(1 + new java.util.Random().nextInt(10000));
			return session.set("stressUpdateUrl", url);
		})
			// Create the ref for this specific virtual user
			.exec(
				http("Create Ref for Concurrent Update")
					.post("/api/v1/ref")
					.header("X-XSRF-TOKEN", "#{csrfToken}")
					.body(StringBody("""
					{
						"url": "#{stressUpdateUrl}",
						"title": "Stress Test Reference",
						"comment": "Ref for testing concurrent updates",
						"tags": ["stresstest", "concurrent"]
					}"""))
					.check(status().is(201))  // Should always be 201 since URLs are unique
					.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
			)
			.pause(Duration.ofMillis(100))
			.exec(
				http("Fetch Ref for Concurrent Update")
					.get("/api/v1/ref")
					.queryParam("url", "#{stressUpdateUrl}")
					.check(status().is(200))
					.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
					.check(jsonPath("$.modified").optional().saveAs("stressRefModified"))
			)
			.doIf(session -> session.contains("stressRefModified")).then(
				exec(
					http("Concurrent Update Attempt")
						.patch("/api/v1/ref")
						.queryParam("url", "#{stressUpdateUrl}")
						.queryParam("cursor", "#{stressRefModified}")
						.header("X-XSRF-TOKEN", "#{csrfToken}")
						.header("Content-Type", "application/merge-patch+json")
						.body(StringBody("""
						{
							"tags": ["updated.#{randomInt(1,1000)}", "concurrent.#{randomLong()}", "stressupdate"],
							"comment": "Updated during stress test at #{randomLong()}"
						}"""))
						.check(status().in(200, 409))
						.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
				)
			).pause(Duration.ofMillis(50), Duration.ofMillis(200));

	// ====================== Graph Operations Stress ======================

	ChainBuilder stressGraphOperations = exec(session -> {
		// Build list of URLs for graph query
		java.util.List<String> urls = java.util.stream.IntStream.range(1, 21)
			.mapToObj(i -> "https://stress-test.example.com/" + (System.currentTimeMillis() + i))
			.collect(java.util.stream.Collectors.toList());
		return session.set("graphUrls", urls);
	})
	.exec(
		http("Stress Graph Query")
			.get("/api/v1/graph/list")
			.multivaluedQueryParam("urls", "#{graphUrls}")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").optional().saveAs("csrfToken"))
			.check(responseTimeInMillis().lt(8000))
	).pause(Duration.ofMillis(300), Duration.ofMillis(1000));

	// ====================== Scenarios ======================

	ScenarioBuilder volumeStress = scenario("High Volume Operations")
		.exec(fetchCsrfToken)
		.repeat(20).on(
			exec(rapidRefCreation)
				.pace(Duration.ofMillis(100))
		)
		.exec(largePageQuery)
		.exec(complexSearchQuery);

	ScenarioBuilder errorHandlingTest = scenario("Error Handling Tests")
		.exec(fetchCsrfToken)
		.repeat(10).on(
			randomSwitch().on(
				percent(40.0).then(exec(testNotFoundErrors)),
				percent(30.0).then(exec(testInvalidData)),
				percent(30.0).then(exec(testMalformedJson))
			)
		);

	ScenarioBuilder systemLimitsTest = scenario("System Limits Test")
		.exec(fetchCsrfToken)
		.exec(createLargeExt)
		.pause(Duration.ofSeconds(1), Duration.ofSeconds(3))
		.exec(stressGraphOperations)
		.pause(Duration.ofSeconds(1), Duration.ofSeconds(2))
		.repeat(5).on(
			exec(concurrentUpdates).pace(Duration.ofMillis(200))
		);

	ScenarioBuilder replicationStress = scenario("Replication Stress")
		.exec(fetchCsrfToken)
		.repeat(15).on(
			exec(testReplicationEndpoints)
				.pace(Duration.ofSeconds(1))
		);

	ScenarioBuilder adminStress = scenario("Administration Stress")
		.exec(fetchCsrfToken)
		.exec(testBackupOperations)
		.pause(Duration.ofSeconds(2), Duration.ofSeconds(5))
		.repeat(8).on(
			exec(testAdminOperations)
				.pace(Duration.ofMillis(500))
		);

	ScenarioBuilder contentEnrichmentStress = scenario("Content Enrichment Stress")
		.exec(fetchCsrfToken)
		.repeat(10).on(
			randomSwitch().on(
				percent(60.0).then(exec(stressProxyOperations)),
				percent(40.0).then(exec(stressScrapeOperations))
			)
		);

	{
		setUp(
			// High volume concurrent operations
			volumeStress.injectOpen(
				nothingFor(Duration.ofSeconds(2)),
				rampUsers(50).during(Duration.ofSeconds(30)),
				constantUsersPerSec(20).during(Duration.ofMinutes(2))
			),
			// Error condition testing
			errorHandlingTest.injectOpen(
				nothingFor(Duration.ofSeconds(5)),
				rampUsers(20).during(Duration.ofSeconds(20)),
				constantUsersPerSec(10).during(Duration.ofMinutes(1))
			),
			// System limits testing
			systemLimitsTest.injectOpen(
				nothingFor(Duration.ofSeconds(10)),
				rampUsers(15).during(Duration.ofSeconds(25)),
				constantUsersPerSec(5).during(Duration.ofMinutes(2))
			),
			// Replication stress
			replicationStress.injectOpen(
				nothingFor(Duration.ofSeconds(8)),
				rampUsers(10).during(Duration.ofSeconds(15)),
				constantUsersPerSec(4).during(Duration.ofMinutes(1))
			),
			// Administration operations
			adminStress.injectOpen(
				nothingFor(Duration.ofSeconds(12)),
				rampUsers(5).during(Duration.ofSeconds(20)),
				constantUsersPerSec(2).during(Duration.ofMinutes(1))
			),
			// Content enrichment stress
			contentEnrichmentStress.injectOpen(
				nothingFor(Duration.ofSeconds(15)),
				rampUsers(8).during(Duration.ofSeconds(30)),
				constantUsersPerSec(3).during(Duration.ofMinutes(2))
			)
		).protocols(httpProtocol)
			.maxDuration(Duration.ofMinutes(4))
			.throttle(
				reachRps(50).in(Duration.ofSeconds(30)),
				holdFor(Duration.ofMinutes(2)),
				jumpToRps(30),
				holdFor(Duration.ofMinutes(1))
			)
			.assertions(
				global().responseTime().max().lt(15000),
				global().responseTime().mean().lt(3000),
				global().successfulRequests().percent().gt(70.0), // Lower success rate expected for stress testing
				details("High Volume Operations").failedRequests().percent().lt(40.0),
				details("Error Handling Tests").responseTime().percentile3().lt(5000)
			);
	}
}
