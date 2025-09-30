package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Realistic User Journey Load Test for Jasper
 *
 * This simulation models realistic user workflows:
 * - Research Session: User researches a topic, saves references, takes notes
 * - Daily Review: User checks recent updates and organizes content
 * - Content Curation: User creates and manages templates and plugins
 * - Collaborative Work: Multiple users working on shared content
 */
public class UserJourneySimulation extends Simulation {

	HttpProtocolBuilder httpProtocol = http
		.baseUrl("http://localhost:8081")
		.acceptHeader("application/json")
		.contentTypeHeader("application/json")
		.userAgentHeader("Gatling Load Test - User Journey")
		.check(status().not(500), status().not(502), status().not(503));

	// ====================== Feeder Data ======================

	FeederBuilder<Object> topicFeeder = listFeeder(java.util.Arrays.asList(
		Map.of("topic", "Machine Learning", "category", "AI"),
		Map.of("topic", "Microservices", "category", "Architecture"),
		Map.of("topic", "Data Science", "category", "Analytics"),
		Map.of("topic", "Kubernetes", "category", "DevOps"),
		Map.of("topic", "React Development", "category", "Frontend"),
		Map.of("topic", "Database Design", "category", "Backend"),
		Map.of("topic", "Security Best Practices", "category", "Security"),
		Map.of("topic", "API Design", "category", "Architecture")
	)).circular();

	FeederBuilder<Object> sourceFeeder = listFeeder(java.util.Arrays.asList(
		Map.of("source", "arxiv.org", "type", "paper"),
		Map.of("source", "github.com", "type", "code"),
		Map.of("source", "medium.com", "type", "article"),
		Map.of("source", "stackoverflow.com", "type", "qa"),
		Map.of("source", "docs.spring.io", "type", "documentation"),
		Map.of("source", "kubernetes.io", "type", "official-docs"),
		Map.of("source", "martinfowler.com", "type", "blog"),
		Map.of("source", "youtube.com", "type", "video")
	)).circular();

	// ====================== CSRF Token Setup ======================

	ChainBuilder fetchCsrfToken = exec(
		http("Fetch CSRF Token")
			.get("/api/v1/ref/page")
			.queryParam("size", "1")
			.check(status().is(200))
			.check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").saveAs("csrfToken"))
	);

	// ====================== Research Session Journey ======================

	ChainBuilder researchWorkflow = feed(topicFeeder)
		.exec(session -> {
			System.out.println("User researching: " + session.getString("topic"));
			return session;
		})
		// Start by searching for existing knowledge
		.exec(
			http("Search Existing Knowledge - #{topic}")
				.get("/api/v1/ref/page")
				.queryParam("search", "#{topic}")
				.queryParam("size", "20")
				.check(status().is(200))
				.check(jsonPath("$.content").saveAs("existingRefs"))
		)
		.pause(Duration.ofSeconds(2, 5))
		// Browse by category tag
		.exec(
			http("Browse by Category - #{category}")
				.get("/api/v1/ref/page")
				.queryParam("query", "#{category}")
				.queryParam("size", "15")
				.check(status().is(200))
		)
		.pause(Duration.ofSeconds(1, 3))
		// Create a new reference with research findings
		.feed(sourceFeeder)
		.exec(session -> {
			String url = "https://" + session.getString("source") + "/" + session.getString("topic") + "-research-" + System.currentTimeMillis();
			return session.set("researchUrl", url);
		})
		.exec(
			http("Save Research Reference - #{topic}")
				.post("/api/v1/ref")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.body(StringBody("""
					{
						"url": "#{researchUrl}",
						"title": "#{topic} Research - Study#{randomInt(1000,9999)}",
						"comment": "Research findings on #{topic} from #{source}",
						"tags": ["research", "#{category}", "#{type}"]
					}"""))
				.check(status().is(201))
				.check(jsonPath("$").saveAs("createdTimestamp"))
		)
		.pause(Duration.ofMillis(500))
		// Verify the created reference
		.exec(
			http("Verify Created Research Reference")
				.get("/api/v1/ref")
				.queryParam("url", "#{researchUrl}")
				.check(status().is(200))
				.check(jsonPath("$.url").isEL("#{researchUrl}"))
		)
		.pause(Duration.ofMillis(500))
		// Add a comment/note about the research
		.exec(session -> {
			String commentUrl = "comment:" + java.util.UUID.randomUUID().toString();
			return session.set("commentUrl", commentUrl);
		})
		.exec(
			http("Add Research Note - #{topic}")
				.post("/api/v1/ref")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.body(StringBody("""
					{
						"url": "#{commentUrl}",
						"title": "Research Notes: #{topic}",
						"comment": "Key insights and takeaways from #{topic} research session",
						"tags": ["note", "#{category}", "research.summary"],
						"sources": ["#{researchUrl}"]
					}"""))
				.check(status().is(201))
		)
		.pause(Duration.ofSeconds(1, 3))
		// Check if there are related templates
		.exec(
			http("Find Related Templates - #{category}")
				.get("/api/v1/template/page")
				.queryParam("query", "#{category}")
				.queryParam("size", "10")
				.check(status().is(200))
		);

	// ====================== Daily Review Journey ======================

	ChainBuilder dailyReviewWorkflow = exec(session -> {
		System.out.println("User performing daily review");
		return session;
	})
		// Check recent activity
		.exec(
			http("Check Recent Updates")
				.get("/api/v1/ref/page")
				.queryParam("size", "30")
				.queryParam("sort", "modified,desc")
				.check(status().is(200))
				.check(jsonPath("$.content[*].url").findAll().saveAs("recentUrls"))
		)
		.pause(Duration.ofSeconds(2, 4))
		// Review specific items
		.exec(
			http("Review Specific Item")
				.get("/api/v1/ref")
				.queryParam("url", "#{researchUrl}")
				.check(status().is(200))
		)
		.pause(Duration.ofSeconds(1, 2))
		// Look for untagged content to organize
		.exec(
			http("Find Untagged Content")
				.get("/api/v1/ref/page")
				.queryParam("untagged", "true")
				.queryParam("size", "10")
				.check(status().is(200))
		)
		.pause(Duration.ofSeconds(2, 4))
		// Fetch a ref to update (to get its cursor/modified timestamp)
		.exec(
			http("Fetch Ref for Update")
				.get("/api/v1/ref")
				.queryParam("url", "#{researchUrl}")
				.check(status().is(200))
				.check(
					jsonPath("$.modified").saveAs("refModified")
				)
		)
		.pause(Duration.ofMillis(500))
		// Update a reference with additional tags (using merge-patch)
		.exec(
			http("Update Reference Tags")
				.patch("/api/v1/ref")
				.queryParam("url", "#{researchUrl}")
				.queryParam("cursor", "#{refModified}")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.header("Content-Type", "application/merge-patch+json")
				.body(StringBody("""
					{
						"tags": ["organized", "daily.review", "review#{randomInt(10000,99999)}"]
					}"""))
				.check(status().is(200))
		);

	// ====================== Content Curation Journey ======================

	ChainBuilder curationWorkflow = feed(topicFeeder)
		.exec(session -> {
			System.out.println("User curating content for: " + session.getString("topic"));
			return session;
		})
		// Create a specialized template for the topic
		.exec(
			http("Create Topic Template - #{topic}")
				.post("/api/v1/template")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.body(StringBody("""
					{
						"tag": "_template/#{topic}.#{randomInt(1,100)}",
						"name": "#{topic} Resource Template",
						"config": {
							"description": "Template for organizing #{topic} resources",
							"category": "#{category}"
						},
						"defaults": {
							"difficulty": "beginner",
							"resource_type": "tutorial",
							"quality_rating": 5
						},
						"schema": {
							"optionalProperties": {
								"difficulty": {"enum": ["beginner", "intermediate", "advanced"]},
								"resource_type": {"enum": ["tutorial", "reference", "example", "tool"]},
								"quality_rating": {"type": "float64"}
							}
						}
					}"""))
				.check(status().in(201, 409)) // 201 Created or 409 Conflict if already exists
		)
		.pause(Duration.ofSeconds(1, 2))
		// Create a plugin for enhanced functionality
		.exec(
			http("Create Enhancement Plugin - #{topic}")
				.post("/api/v1/plugin")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.body(StringBody("""
					{
						"tag": "+plugin/#{topic}.enhancer.#{randomInt(1,100)}",
						"name": "#{topic} Content Enhancer",
						"config": {
							"type": "enhancer",
							"description": "Enhances #{topic} content with additional metadata",
							"selector": ".#{category}-content",
							"features": ["auto-tagging", "difficulty-detection", "related-content"]
						}
					}"""))
				.check(status().in(201, 409)) // 201 Created or 409 Conflict if already exists
		)
		.pause(Duration.ofSeconds(1, 3))
		// Check existing extensions for the topic
		.exec(
			http("Browse Topic Extensions - #{category}")
				.get("/api/v1/ext/page")
				.queryParam("query", "#{category}")
				.queryParam("size", "15")
				.check(status().is(200))
		)
		.pause(Duration.ofSeconds(1, 2))
		// Create a curated collection
		.exec(
			http("Create Curated Collection - #{topic}")
				.post("/api/v1/ext")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.body(StringBody("""
					{
						"tag": "+collection/#{topic}-#{randomInt(1,50)}",
						"name": "#{topic} Curated Collection",
						"config": {
							"type": "collection",
							"description": "Carefully curated resources for learning #{topic}",
							"curator": "system",
							"created": "#{randomLong()}",
							"criteria": {
								"quality_min": 7,
								"categories": ["#{category}"],
								"verified": true
							}
						}
					}"""))
				.check(status().in(201, 409)) // 201 Created or 409 Conflict if already exists
		);

	// ====================== Collaborative Work Journey ======================

	ChainBuilder collaborationWorkflow = exec(session -> {
		System.out.println("User engaging in collaborative work");
		return session;
	})
		// Check user information and permissions
		.exec(
			http("Check User Profile")
				.get("/api/v1/user/whoami")
				.check(status().is(200))
		)
		.pause(Duration.ofSeconds(1))
		// Look for shared content
		.exec(
			http("Browse Shared Content")
				.get("/api/v1/ref/page")
				.queryParam("query", "shared")
				.queryParam("size", "20")
				.check(status().is(200))
		)
		.pause(Duration.ofSeconds(1, 3))
		// Add a collaborative comment
		.exec(session -> {
			String commentUrl = "comment:collaboration-" + java.util.UUID.randomUUID().toString();
			return session.set("collabCommentUrl", commentUrl);
		})
		.exec(
			http("Add Collaborative Comment")
				.post("/api/v1/ref")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.body(StringBody("""
					{
						"url": "#{collabCommentUrl}",
						"title": "Team Discussion Point",
						"comment": "Adding my thoughts on this topic for team review",
						"tags": ["collaboration", "team.input", "discussion"],
						"sources": ["https://example.com/shared-doc-#{randomInt(1,20)}"]
					}"""))
				.check(status().is(201))
		)
		.pause(Duration.ofSeconds(1, 2))
		// Check graph relationships for collaborative content
		.exec(
			http("Check Collaboration Graph")
				.get("/api/v1/graph/list")
				.queryParam("urls", java.util.Arrays.asList(
					"https://example.com/shared-doc-1",
					"https://example.com/shared-doc-2",
					"comment:collaboration-session#{randomInt(1000,9999)}"
				))
				.check(status().is(200))
		)
		.pause(Duration.ofSeconds(1, 3))
		// Create a shared template
		.exec(
			http("Create Shared Template")
				.post("/api/v1/template")
				.header("X-XSRF-TOKEN", "#{csrfToken}")
				.body(StringBody("""
					{
						"tag": "_template/team.standard.#{randomInt(1,20)}",
						"name": "Team Standard Template",
						"config": {
							"description": "Standardized template for team collaboration",
							"shared": true,
							"team": "development"
						},
						"defaults": {
							"assignee": "",
							"priority": "medium",
							"status": "draft"
						},
						"schema": {
							"properties": {
								"assignee": {"type": "string"}
							},
							"optionalProperties": {
								"priority": {"enum": ["low", "medium", "high", "urgent"]},
								"status": {"enum": ["draft", "review", "approved", "archived"]}
							}
						}
					}"""))
				.check(status().in(201, 409)) // 201 Created or 409 Conflict if already exists
		);

	// ====================== Scenarios ======================

	ScenarioBuilder researchSession = scenario("Research Session")
		.exec(fetchCsrfToken)
		.exec(researchWorkflow)
		.pause(Duration.ofSeconds(5, 10))
		.repeat(2).on(
			exec(researchWorkflow).pause(Duration.ofSeconds(10, 20))
		);

	ScenarioBuilder dailyReview = scenario("Daily Review")
		.exec(fetchCsrfToken)
		.exec(dailyReviewWorkflow)
		.pause(Duration.ofSeconds(3, 8));

	ScenarioBuilder contentCuration = scenario("Content Curation")
		.exec(fetchCsrfToken)
		.exec(curationWorkflow)
		.pause(Duration.ofSeconds(8, 15));

	ScenarioBuilder collaborativeWork = scenario("Collaborative Work")
		.exec(fetchCsrfToken)
		.exec(collaborationWorkflow)
		.pause(Duration.ofSeconds(5, 12));

	// Mixed realistic user behavior
	ScenarioBuilder realisticUser = scenario("Realistic User Behavior")
		.exec(fetchCsrfToken)
		.randomSwitch().on(
			percent(40.0).then(exec(dailyReviewWorkflow)),
			percent(30.0).then(exec(researchWorkflow)),
			percent(20.0).then(exec(collaborationWorkflow)),
			percent(10.0).then(exec(curationWorkflow))
		)
		.pause(Duration.ofSeconds(10, 30));

	{
		setUp(
			// Research sessions - intensive but focused
			researchSession.injectOpen(
				nothingFor(Duration.ofSeconds(5)),
				rampUsers(8).during(Duration.ofSeconds(30)),
				constantUsersPerSec(3).during(Duration.ofMinutes(2))
			),
			// Daily reviews - regular, moderate activity
			dailyReview.injectOpen(
				nothingFor(Duration.ofSeconds(2)),
				rampUsers(15).during(Duration.ofSeconds(20)),
				constantUsersPerSec(7).during(Duration.ofMinutes(3))
			),
			// Content curation - specialized, lower frequency
			contentCuration.injectOpen(
				nothingFor(Duration.ofSeconds(10)),
				rampUsers(4).during(Duration.ofSeconds(25)),
				constantUsersPerSec(1).during(Duration.ofMinutes(2))
			),
			// Collaborative work - sporadic but important
			collaborativeWork.injectOpen(
				nothingFor(Duration.ofSeconds(8)),
				rampUsers(6).during(Duration.ofSeconds(20)),
				constantUsersPerSec(2).during(Duration.ofMinutes(2))
			),
			// Realistic mixed behavior - most common pattern
			realisticUser.injectOpen(
				nothingFor(Duration.ofSeconds(1)),
				rampUsers(25).during(Duration.ofSeconds(45)),
				constantUsersPerSec(12).during(Duration.ofMinutes(4))
			)
		).protocols(httpProtocol)
		.maxDuration(Duration.ofMinutes(5))
		.assertions(
			global().responseTime().max().lt(8000),
			global().responseTime().mean().lt(2000),
			global().successfulRequests().percent().gt(75.0),
			details("Research Session").responseTime().percentile3().lt(4000),
			details("Daily Review").responseTime().percentile3().lt(3000)
		);
	}
}
