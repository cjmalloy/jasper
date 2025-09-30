package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jasper.component.ConfigCache;
import jasper.security.Auth;
import jasper.service.dto.TemplateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Profile("limit")
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {
	private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Auth auth;

	// Map to store per-origin rate limiters
	private final ConcurrentHashMap<String, RateLimiter> originRateLimiters = new ConcurrentHashMap<>();
	
	// Global bulkhead for HTTP requests
	private volatile Bulkhead globalBulkhead;

	private RateLimiter getOriginRateLimiter(String origin) {
		return originRateLimiters.computeIfAbsent(origin, k -> {
			var maxConcurrent = configs.root().getMaxConcurrentRequestsPerOrigin();
			logger.debug("{} Creating rate limiter for origin with {} permits per second", origin, maxConcurrent);
			
			var rateLimiterConfig = RateLimiterConfig.custom()
				.limitForPeriod(maxConcurrent)
				.limitRefreshPeriod(Duration.ofSeconds(1))
				.timeoutDuration(Duration.ofMillis(0)) // Don't wait, fail fast
				.build();
			
			return RateLimiter.of("http-" + origin, rateLimiterConfig);
		});
	}
	
	private Bulkhead getGlobalBulkhead() {
		if (globalBulkhead == null) {
			synchronized (this) {
				if (globalBulkhead == null) {
					globalBulkhead = createGlobalBulkhead();
				}
			}
		}
		return globalBulkhead;
	}
	
	private Bulkhead createGlobalBulkhead() {
		var maxConcurrent = configs.root().getMaxConcurrentRequests();
		// Check environment variable override
		var env = System.getenv("JASPER_MAX_CONCURRENT_REQUESTS");
		if (env != null && !env.isEmpty()) {
			try {
				maxConcurrent = Integer.parseInt(env);
			} catch (NumberFormatException e) {
				logger.warn("Invalid JASPER_MAX_CONCURRENT_REQUESTS value: {}", env);
			}
		}
		
		logger.info("Creating global HTTP request bulkhead with {} permits", maxConcurrent);
		
		var bulkheadConfig = BulkheadConfig.custom()
			.maxConcurrentCalls(maxConcurrent)
			.maxWaitDuration(Duration.ofMillis(0)) // Don't wait, fail fast
			.build();
		
		return Bulkhead.of("http-global", bulkheadConfig);
	}
	
	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		// Check if this is a server config update
		if (template.getTag() != null && template.getTag().startsWith("_config/server")) {
			logger.info("Server config updated, recreating global bulkhead");
			synchronized (this) {
				globalBulkhead = createGlobalBulkhead();
			}
			// Clear origin rate limiters to pick up new config
			originRateLimiters.clear();
		}
	}

	@Bean
	public HandlerInterceptor rateLimitInterceptor() {
		return new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
				var origin = auth.getOrigin();
				
				// Try global bulkhead first
				var bulkhead = getGlobalBulkhead();
				if (!bulkhead.tryAcquirePermission()) {
					logger.warn("Global HTTP rate limit exceeded for request: {}", request.getRequestURI());
					response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					var retryAfter = String.format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5));
					response.setHeader("X-RateLimit-Retry-After", retryAfter);
					return false;
				}
				
				// Then try per-origin rate limiter
				var rateLimiter = getOriginRateLimiter(origin);
				if (!rateLimiter.acquirePermission()) {
					// Release global bulkhead permit since we're rejecting
					bulkhead.releasePermission();
					
					logger.warn("{} Rate limit exceeded for origin: {}", origin, request.getRequestURI());
					response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					var retryAfter = String.format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5));
					response.setHeader("X-RateLimit-Retry-After", retryAfter);
					return false;
				}
				
				// Store bulkhead reference for cleanup
				request.setAttribute("global-bulkhead", bulkhead);
				return true;
			}

			@Override
			public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
				// Release global bulkhead permit
				var bulkhead = (Bulkhead) request.getAttribute("global-bulkhead");
				if (bulkhead != null) {
					bulkhead.releasePermission();
				}
				// No cleanup needed for RateLimiter - it handles timing automatically
			}
		};
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor());
	}
}