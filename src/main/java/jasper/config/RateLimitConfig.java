package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
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
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.String.format;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {
	private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Auth auth;

	@Autowired
	Bulkhead httpBulkhead;

	private final ConcurrentHashMap<String, RateLimiter> originRateLimiters = new ConcurrentHashMap<>();
	private RateLimiter getOriginRateLimiter(String origin) {
		return originRateLimiters.computeIfAbsent(origin, k -> RateLimiter.of("http-" + origin, RateLimiterConfig.custom()
			.limitForPeriod(configs.security(origin).getMaxConcurrentRequests())
			.limitRefreshPeriod(Duration.ofSeconds(1))
			.build()));
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		if (template.getTag() != null && template.getTag().startsWith("_config/server")) {
			logger.debug("Server config updated, clearing per-origin rate limiters");
			originRateLimiters.clear();
		}
	}

	@Bean
	public HandlerInterceptor rateLimitInterceptor() {
		return new HandlerInterceptor() {
			private static final String HTTP_BULKHEAD_PERMIT_ACQUIRED = "httpBulkheadPermitAcquired";

			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				var origin = auth.getOrigin();
				if (!httpBulkhead.tryAcquirePermission()) {
					logger.debug("Global HTTP rate limit exceeded for request: {}", request.getRequestURI());
					response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					response.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
					return false;
				}
				// Mark that we acquired the permit so we can release it later
				request.setAttribute(HTTP_BULKHEAD_PERMIT_ACQUIRED, true);

				if (!getOriginRateLimiter(origin).acquirePermission()) {
					httpBulkhead.releasePermission();
					// Mark that we released the permit early, so we don't release it again
					request.setAttribute(HTTP_BULKHEAD_PERMIT_ACQUIRED, false);

					logger.debug("{} Rate limit exceeded for origin: {}", origin, request.getRequestURI());
					response.setStatus(429);
					response.setHeader("X-RateLimit-Limit", ""+configs.security(origin).getMaxConcurrentRequests());
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					response.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
					return false;
				}
				return true;
			}

			@Override
			public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
				// Only release the permit if it was actually acquired in preHandle
				if (Boolean.TRUE.equals(request.getAttribute(HTTP_BULKHEAD_PERMIT_ACQUIRED))) {
					httpBulkhead.releasePermission();
				}
			}
		};
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor());
	}
}
