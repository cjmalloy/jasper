package simulations.jasper;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Duration;

/**
 * Utility class for handling rate limit retries in Gatling tests.
 * 
 * Provides functionality to:
 * - Check for 429 (Too Many Requests) and 503 (Service Unavailable) responses
 * - Extract X-RateLimit-Retry-After header and parse the delay
 * - Automatically retry requests after the specified delay
 * 
 * The utility supports both integer seconds and ISO-8601 datetime formats for the retry delay.
 */
public class RateLimitRetry {
    
    /**
     * Parse the Retry-After header value.
     * Supports both seconds (integer) and HTTP-date format (RFC 7231)
     * 
     * @param retryAfter The Retry-After header value
     * @return Delay in seconds
     */
    public static long parseRetryAfter(String retryAfter) {
        if (retryAfter == null || retryAfter.trim().isEmpty()) {
            return 5; // Default 5 seconds if no header
        }
        
        try {
            // Try parsing as seconds first (most common for rate limiting)
            return Long.parseLong(retryAfter.trim());
        } catch (NumberFormatException e) {
            // If not a number, try parsing as HTTP-date (RFC 7231) or ISO-8601
            try {
                java.time.Instant targetTime = java.time.Instant.parse(retryAfter.trim());
                java.time.Instant now = java.time.Instant.now();
                long seconds = java.time.Duration.between(now, targetTime).getSeconds();
                return Math.max(1, seconds); // Don't return 0 or negative values
            } catch (Exception ex) {
                // If parsing fails, default to 5 seconds
                System.err.println("Failed to parse Retry-After header: '" + retryAfter + "', using 5s default");
                return 5;
            }
        }
    }
    
    /**
     * Create a chain that handles rate limit responses (429/503) with retry logic.
     * 
     * This method wraps an HTTP request and adds automatic retry logic when the server
     * responds with 429 or 503 status codes. It:
     * 1. Checks the response status
     * 2. If 429 or 503, extracts the X-RateLimit-Retry-After header
     * 3. Pauses for the specified duration (or uses exponential backoff)
     * 4. Retries the request
     * 
     * @param requestName Name for the request (used in logs)
     * @param request Function that builds the HTTP request
     * @param maxRetries Maximum number of retry attempts (default: 2)
     * @return ChainBuilder with retry logic
     */
    public static ChainBuilder withRateLimitRetry(
            String requestName,
            java.util.function.Function<Session, HttpRequestActionBuilder> request,
            int maxRetries) {
        
        ChainBuilder chain = exec(
            session -> request.apply(session)
                .check(status().saveAs("lastStatus"))
                .check(header("X-RateLimit-Retry-After").optional().saveAs("retryAfter"))
        );
        
        // Add retry loops
        for (int i = 0; i < maxRetries; i++) {
            final int retryNum = i + 1;
            chain = chain.doIf(session -> {
                Integer status = session.getInt("lastStatus");
                return status != null && (status == 429 || status == 503);
            }).then(
                exec(session -> {
                    Integer status = session.getInt("lastStatus");
                    String retryAfter = session.getString("retryAfter");
                    
                    long delaySeconds;
                    if (retryAfter != null && !retryAfter.isEmpty()) {
                        delaySeconds = parseRetryAfter(retryAfter);
                        System.out.println(requestName + ": Rate limited (status=" + status + 
                            "), retry " + retryNum + "/" + maxRetries + 
                            " after " + delaySeconds + "s (from X-RateLimit-Retry-After header)");
                    } else {
                        // Exponential backoff: 1s, 2s, 4s, 8s, ...
                        delaySeconds = (long) Math.pow(2, retryNum - 1);
                        System.out.println(requestName + ": Rate limited (status=" + status + 
                            "), retry " + retryNum + "/" + maxRetries + 
                            " after " + delaySeconds + "s (exponential backoff)");
                    }
                    
                    return session.set("retryDelay", delaySeconds);
                })
                .pause(session -> Duration.ofSeconds(session.getLong("retryDelay")))
                .exec(session -> request.apply(session)
                    .check(status().saveAs("lastStatus"))
                    .check(header("X-RateLimit-Retry-After").optional().saveAs("retryAfter"))
                )
            );
        }
        
        return chain;
    }
    
    /**
     * Create a chain with default max retries of 2
     */
    public static ChainBuilder withRateLimitRetry(
            String requestName,
            java.util.function.Function<Session, HttpRequestActionBuilder> request) {
        return withRateLimitRetry(requestName, request, 2);
    }
}
