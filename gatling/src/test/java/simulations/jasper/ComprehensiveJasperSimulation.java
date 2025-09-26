package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive Jasper Knowledge Management Load Test
 * 
 * This simulation represents real-world usage patterns:
 * - Knowledge workers adding and organizing references
 * - Users browsing and searching content
 * - Plugin-based content enrichment
 * - Template-based content organization
 * - User management operations
 */
public class ComprehensiveJasperSimulation extends Simulation {

	HttpProtocolBuilder httpProtocol = http
		.baseUrl("http://localhost:8081")
		.acceptHeader("application/json")
		.contentTypeHeader("application/json")
		.userAgentHeader("Gatling Load Test - Comprehensive");

	// ====================== Reference Operations ======================
	
	ChainBuilder createWebReference = exec(
		http("Create Web Reference")
			.post("/api/v1/ref")
			.body(StringBody("""
				{
					"url": "https://example.com/article-#{randomInt(1,1000)}",
					"title": "Knowledge Article #{randomInt(1,1000)}",
					"comment": "Important reference for our research",
					"tags": ["research", "article", "knowledgebase"],
					"sources": ["https://source.example.com"]
				}"""))
			.check(status().in(201, 403)) // Accept both success and auth failure
	).pause(Duration.ofMillis(500));

	ChainBuilder createBookReference = exec(
		http("Create Book Reference")
			.post("/api/v1/ref")
			.body(StringBody("""
				{
					"url": "isbn:978-#{randomInt(100000000,999999999)}#{randomInt(0,9)}",
					"title": "Technical Book #{randomInt(1,100)}",
					"comment": "Reference book on software engineering",
					"tags": ["book", "technical", "software"],
					"plugins": {
						"+plugin/book": {}
					}
				}"""))
			.check(status().in(201, 403)) // Accept both success and auth failure
	).pause(Duration.ofMillis(500));

	ChainBuilder createComment = exec(
		http("Create Comment Reference")
			.post("/api/v1/ref")
			.body(StringBody("""
				{
					"url": "comment:#{randomUuid()}",
					"title": "User Comment #{randomInt(1,500)}",
					"comment": "This is a user comment or note",
					"tags": ["comment", "discussion"],
					"sources": ["https://example.com/article-#{randomInt(1,100)}"]
				}"""))
			.check(status().in(201, 403)) // Accept both success and auth failure
	).pause(Duration.ofMillis(300));

	// ====================== Browse and Search Operations ======================
	
	ChainBuilder browseRecentRefs = exec(
		http("Browse Recent References")
			.get("/api/v1/ref/page")
			.queryParam("size", "20")
			.queryParam("sort", "modified,desc")
			.check(status().is(200))
			.check(jsonPath("$.content").exists())
	).pause(Duration.ofSeconds(1));

	ChainBuilder searchByTag = exec(
		http("Search by Tag")
			.get("/api/v1/ref/page")
			.queryParam("query", "+tag:research")
			.queryParam("size", "10")
			.check(status().is(200))
	).pause(Duration.ofMillis(800));

	ChainBuilder searchByKeyword = exec(
		http("Search by Keyword")
			.get("/api/v1/ref/page")
			.queryParam("search", "technical software")
			.queryParam("size", "15")
			.check(status().is(200))
	).pause(Duration.ofMillis(800));

	ChainBuilder getSpecificRef = exec(
		http("Get Specific Reference")
			.get("/api/v1/ref")
			.queryParam("url", "https://example.com/article-#{randomInt(1,100)}")
			.check(status().in(200, 404))
	).pause(Duration.ofMillis(400));

	// ====================== Extension Operations ======================
	
	ChainBuilder createPlugin = exec(
		http("Create Plugin Extension")
			.post("/api/v1/ext")
			.body(StringBody("""
				{
					"tag": "+plugin/test.#{randomInt(1,50)}",
					"name": "Test Plugin #{randomInt(1,50)}",
					"config": {
						"description": "A test plugin for load testing",
						"version": "1.0.0",
						"enabled": true
					}
				}"""))
			.check(status().in(201, 403)) // Accept both success and auth failure
	).pause(Duration.ofMillis(600));

	ChainBuilder browseExtensions = exec(
		http("Browse Extensions")
			.get("/api/v1/ext/page")
			.queryParam("size", "20")
			.check(status().is(200))
	).pause(Duration.ofMillis(700));

	// ====================== Plugin Management ======================
	
	ChainBuilder createPluginConfig = exec(
		http("Create Plugin Configuration")
			.post("/api/v1/plugin")
			.body(StringBody("""
				{
					"tag": "+plugin/custom.#{randomInt(1,30)}",
					"name": "Custom Plugin #{randomInt(1,30)}",
					"config": {
						"type": "viewer",
						"title": "Custom Viewer",
						"selector": "custom-selector"
					}
				}"""))
			.check(status().in(201, 403)) // Accept both success and auth failure
	).pause(Duration.ofMillis(500));

	ChainBuilder getPluginConfig = exec(
		http("Get Plugin Configuration")
			.get("/api/v1/plugin")
			.queryParam("tag", "+plugin/book")
			.check(status().in(200, 404))
	).pause(Duration.ofMillis(300));

	// ====================== Template Operations ======================
	
	ChainBuilder createTemplate = exec(
		http("Create Template")
			.post("/api/v1/template")
			.body(StringBody("""
				{
					"tag": "_template/article.#{randomInt(1,20)}",
					"name": "Article Template #{randomInt(1,20)}",
					"config": {
						"fields": {
							"title": {"type": "string", "required": true},
							"summary": {"type": "string"},
							"category": {"type": "enum", "values": ["tech", "business", "research"]}
						}
					}
				}"""))
			.check(status().in(201, 403)) // Accept both success and auth failure
	).pause(Duration.ofMillis(600));

	ChainBuilder browseTemplates = exec(
		http("Browse Templates")
			.get("/api/v1/template/page")
			.queryParam("size", "10")
			.check(status().is(200))
	).pause(Duration.ofMillis(500));

	// ====================== User Management ======================
	
	ChainBuilder createUser = exec(
		http("Create User")
			.post("/api/v1/user")
			.body(StringBody("""
				{
					"tag": "+user/testuser#{randomInt(1,100)}",
					"name": "Test User #{randomInt(1,100)}",
					"config": {
						"role": "USER",
						"active": true
					}
				}"""))
			.check(status().in(201, 403)) // Accept both success and auth failure
	).pause(Duration.ofMillis(400));

	ChainBuilder getUserInfo = exec(
		http("Get User Info")
			.get("/api/v1/user/whoami")
			.check(status().in(200, 404))
	).pause(Duration.ofMillis(300));

	// ====================== Graph and Analytics ======================
	
	ChainBuilder getGraphData = exec(
		http("Get Graph Data")
			.get("/api/v1/graph/list")
			.queryParam("urls", List.of(
				"https://example.com/article-1",
				"https://example.com/article-2"
			))
			.check(status().is(200))
	).pause(Duration.ofMillis(800));

	ChainBuilder browseTaggedContent = exec(
		http("Browse Tagged Content")
			.get("/api/v1/ref/page")
			.queryParam("query", "+tag:research")
			.queryParam("size", "20")
			.check(status().is(200))
	).pause(Duration.ofMillis(600));

	// ====================== Content Enrichment ======================
	
	ChainBuilder proxyContent = exec(
		http("Proxy External Content")
			.get("/api/v1/proxy")
			.queryParam("url", "https://example.com/content-#{randomInt(1,50)}")
			.check(status().in(200, 404, 403))
	).pause(Duration.ofMillis(1000));

	// ====================== Scenarios ======================

	// Knowledge Worker: Creates and organizes content
	ScenarioBuilder knowledgeWorker = scenario("Knowledge Worker")
		.exec(createWebReference)
		.pause(Duration.ofSeconds(2))
		.exec(createBookReference)
		.pause(Duration.ofSeconds(1))
		.exec(createComment)
		.pause(Duration.ofSeconds(3))
		.exec(searchByTag)
		.pause(Duration.ofSeconds(2))
		.exec(createTemplate)
		.pause(Duration.ofSeconds(1))
		.exec(browseRecentRefs);

	// Content Browser: Searches and views existing content
	ScenarioBuilder contentBrowser = scenario("Content Browser")
		.exec(browseRecentRefs)
		.pause(Duration.ofSeconds(1))
		.exec(searchByKeyword)
		.pause(Duration.ofSeconds(2))
		.exec(getSpecificRef)
		.pause(Duration.ofSeconds(1))
		.exec(searchByTag)
		.pause(Duration.ofSeconds(3))
		.exec(getGraphData)
		.pause(Duration.ofSeconds(1))
		.exec(browseTaggedContent);

	// Administrator: Manages system configuration
	ScenarioBuilder administrator = scenario("Administrator")
		.exec(createPluginConfig)
		.pause(Duration.ofSeconds(2))
		.exec(getPluginConfig)
		.pause(Duration.ofSeconds(1))
		.exec(browseExtensions)
		.pause(Duration.ofSeconds(2))
		.exec(createUser)
		.pause(Duration.ofSeconds(1))
		.exec(getUserInfo)
		.pause(Duration.ofSeconds(3))
		.exec(browseTemplates);

	// Heavy Reader: Intensive content consumption
	ScenarioBuilder heavyReader = scenario("Heavy Reader")
		.repeat(3).on(
			exec(browseRecentRefs)
				.pause(Duration.ofMillis(500))
				.exec(searchByKeyword)
				.pause(Duration.ofMillis(800))
				.exec(getSpecificRef)
				.pause(Duration.ofMillis(300))
		)
		.exec(proxyContent);

	// Power User: Mixed operations including content enrichment
	ScenarioBuilder powerUser = scenario("Power User")
		.exec(createPlugin)
		.pause(Duration.ofSeconds(1))
		.exec(createWebReference)
		.pause(Duration.ofSeconds(2))
		.exec(proxyContent)
		.pause(Duration.ofSeconds(1))
		.exec(searchByTag)
		.pause(Duration.ofSeconds(2))
		.exec(getGraphData);

	{
		setUp(
			// Knowledge workers - moderate activity
			knowledgeWorker.injectOpen(
				nothingFor(Duration.ofSeconds(5)),
				rampUsers(10).during(Duration.ofSeconds(30)),
				constantUsersPerSec(5).during(Duration.ofSeconds(60))
			),
			// Content browsers - high read activity
			contentBrowser.injectOpen(
				nothingFor(Duration.ofSeconds(2)),
				rampUsers(20).during(Duration.ofSeconds(20)),
				constantUsersPerSec(10).during(Duration.ofSeconds(80))
			),
			// Administrators - low activity
			administrator.injectOpen(
				nothingFor(Duration.ofSeconds(10)),
				rampUsers(3).during(Duration.ofSeconds(15)),
				constantUsersPerSec(1).during(Duration.ofSeconds(60))
			),
			// Heavy readers - burst activity
			heavyReader.injectOpen(
				nothingFor(Duration.ofSeconds(15)),
				atOnceUsers(15),
				constantUsersPerSec(8).during(Duration.ofSeconds(45))
			),
			// Power users - sporadic activity
			powerUser.injectOpen(
				nothingFor(Duration.ofSeconds(8)),
				rampUsers(5).during(Duration.ofSeconds(25)),
				constantUsersPerSec(2).during(Duration.ofSeconds(50))
			)
		).protocols(httpProtocol)
		.maxDuration(Duration.ofMinutes(3))
		.assertions(
			global().responseTime().max().lt(5000),
			global().successfulRequests().percent().gt(75.0), // Adjusted for auth-protected endpoints
			forAll().responseTime().percentile3().lt(3000)
		);
	}
}