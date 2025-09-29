package jasper.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jasper.component.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.security.Auth.LOCAL_ORIGIN_HEADER;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("limit")
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {
	private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Props props;

	// Map to store per-origin rate limiters
	private final ConcurrentHashMap<String, RateLimiter> originRateLimiters = new ConcurrentHashMap<>();

	private RateLimiter getOriginRateLimiter(String origin) {
		return originRateLimiters.computeIfAbsent(origin, k -> {
			var maxConcurrent = configs.root().getMaxConcurrentRequestsPerOrigin();
			logger.debug("Creating rate limiter for origin {} with {} permits per second", origin, maxConcurrent);
			
			var rateLimiterConfig = RateLimiterConfig.custom()
				.limitForPeriod(maxConcurrent)
				.limitRefreshPeriod(Duration.ofSeconds(1))
				.timeoutDuration(Duration.ofMillis(0)) // Don't wait, fail fast
				.build();
			
			return RateLimiter.of("http-" + origin, rateLimiterConfig);
		});
	}

	private String resolveOrigin(HttpServletRequest request) {
		var originHeader = request.getHeader(LOCAL_ORIGIN_HEADER);
		if (isNotBlank(originHeader)) {
			originHeader = originHeader.toLowerCase();
			if ("default".equals(originHeader)) return props.getLocalOrigin();
			// Use originHeader if it's valid
			if (originHeader.matches("^@?[a-zA-Z0-9._-]*$")) {
				return originHeader.startsWith("@") ? originHeader : "@" + originHeader;
			}
		}
		return props.getOrigin();
	}

	@Bean
	public HandlerInterceptor rateLimitInterceptor() {
		return new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
				var origin = resolveOrigin(request);
				var rateLimiter = getOriginRateLimiter(origin);
				
				if (!rateLimiter.acquirePermission()) {
					logger.warn("{} Rate limit exceeded for origin: {}", origin, request.getRequestURI());
					response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					response.setHeader("X-RateLimit-Retry-After", "1");
					return false;
				}
				
				// Store origin for logging purposes
				request.setAttribute("rate-limit-origin", origin);
				return true;
			}

			@Override
			public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
				// No cleanup needed with RateLimiter - it handles timing automatically
			}
		};
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor());
	}
}