package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static simulations.jasper.RateLimitRetry.withRateLimitRetry;

import java.time.Duration;

/**
 * Example simulation demonstrating rate limit retry functionality.
 * 
 * This is a minimal example showing how to use the RateLimitRetry utility
 * to automatically handle 429 and 503 responses with X-RateLimit-Retry-After header.
 */
public class RateLimitRetryExample extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8081")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling Rate Limit Example");

    // Example 1: Simple request with automatic rate limit retry
    ChainBuilder exampleSimpleRequest = withRateLimitRetry(
        "Simple API Call",
        session -> http("Get References")
            .get("/api/v1/ref/page")
            .queryParam("size", "10")
            .check(status().in(200, 429, 503))
    );

    // Example 2: Request with custom retry count
    ChainBuilder exampleCustomRetryCount = withRateLimitRetry(
        "Custom Retry Count",
        session -> http("Get References (3 retries)")
            .get("/api/v1/ref/page")
            .queryParam("size", "20")
            .check(status().in(200, 429, 503)),
        3  // Allow up to 3 retries
    );

    // Example 3: POST request with rate limit handling
    ChainBuilder examplePostRequest = exec(
        http("Fetch CSRF Token")
            .get("/api/v1/ref/page")
            .queryParam("size", "1")
            .check(status().is(200))
            .check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]+)").saveAs("csrfToken"))
    ).exec(
        withRateLimitRetry(
            "Create Reference",
            session -> http("Create Reference")
                .post("/api/v1/ref")
                .header("X-XSRF-TOKEN", session.getString("csrfToken"))
                .body(StringBody("""
                    {
                        "url": "https://example.com/test-""" + System.currentTimeMillis() + """",
                        "title": "Test Reference",
                        "tags": ["test", "example"]
                    }"""))
                .check(status().in(201, 429, 503))
        )
    );

    ScenarioBuilder rateLimitExample = scenario("Rate Limit Retry Example")
        .exec(exampleSimpleRequest)
        .pause(Duration.ofMillis(500))
        .exec(exampleCustomRetryCount)
        .pause(Duration.ofMillis(500))
        .exec(examplePostRequest);

    {
        // Note: This is just an example simulation, not meant for actual load testing
        // To use this, run: mvn gatling:test -Dgatling.simulationClass=simulations.jasper.RateLimitRetryExample
        setUp(
            rateLimitExample.injectOpen(atOnceUsers(1))
        ).protocols(httpProtocol)
            .maxDuration(Duration.ofSeconds(30));
    }
}
