package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jasper.component.ConfigCache;
import jasper.domain.Template;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {
	private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Auth auth;

	@Autowired
	Bulkhead globalHttpBulkhead;

	private final ConcurrentHashMap<String, RateLimiter> originRateLimiters = new ConcurrentHashMap<>();

	private RateLimiter getOriginRateLimiter(String origin) {
		return originRateLimiters.computeIfAbsent(origin, k -> {
			var maxConcurrent = configs.security(origin).getMaxConcurrentRequests();
			logger.debug("{} Creating rate limiter for origin with {} permits per second", origin, maxConcurrent);

			var rateLimiterConfig = RateLimiterConfig.custom()
				.limitForPeriod(maxConcurrent)
				.limitRefreshPeriod(Duration.ofSeconds(1))
				.timeoutDuration(Duration.ofMillis(0)) // Don't wait, fail fast
				.build();

			return RateLimiter.of("http-" + origin, rateLimiterConfig);
		});
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		// Check if this is a server config update
		if (template.getTag() != null && template.getTag().startsWith("_config/server")) {
			logger.debug("Server config updated, clearing per-origin rate limiters");
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
				var bulkhead = globalHttpBulkhead;
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
