package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jasper.component.ConfigCache;
import jasper.config.Config.SecurityConfig;
import jasper.security.Auth;
import jasper.service.dto.TemplateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Configuration
public class RateLimitConfig {
	private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

	private static final Duration DEFAULT_PERIOD = new SecurityConfig().getMaxRequestsPeriod();

	@Autowired
	ConfigCache configs;

	@Autowired
	Auth auth;

	@Autowired
	Bulkhead httpBulkhead;

	private final ConcurrentHashMap<String, RateLimiter> originRateLimiters = new ConcurrentHashMap<>();
	private RateLimiter getOriginRateLimiter(String origin) {
		return originRateLimiters.computeIfAbsent(origin, k -> createRateLimiter(origin, "http-" + origin,
			configs.security(origin).getMaxRequests(),
			configs.security(origin).getMaxRequestsPeriod()));
	}

	private volatile RateLimiter httpRateLimiter;
	private final Object httpRateLimiterLock = new Object();

	@PostConstruct
	void init() {
		httpRateLimiter = createRateLimiter("", "http",
			configs.root().getMaxRequests(),
			configs.root().getMaxRequestsPeriod());
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		if (isBlank(template.getTag())) return;
		if (template.getTag().startsWith("_config/server")) {
			logger.debug("{} Server config template updated, updating HTTP rate limiter", template.getOrigin());
			synchronized (httpRateLimiterLock) {
				httpRateLimiter = updateRateLimiter(httpRateLimiter, "", "http",
					configs.root().getMaxRequests(),
					configs.root().getMaxRequestsPeriod());
			}
		}
		if (template.getTag().startsWith("_config/security")) {
			originRateLimiters.replaceAll((origin, r) -> updateRateLimiter(r, origin, "http-" + origin,
				configs.security(origin).getMaxRequests(),
				configs.security(origin).getMaxRequestsPeriod()));
		}
	}

	/**
	 * Change the limit in place, or recreate the rate limiter if the refresh period changed,
	 * since resilience4j cannot change the refresh period at runtime.
	 */
	private static RateLimiter updateRateLimiter(RateLimiter rateLimiter, String origin, String name, int limit, Duration period) {
		if (rateLimiter == null || !Objects.equals(rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod(), validPeriod(origin, period))) {
			return createRateLimiter(origin, name, limit, period);
		}
		if (rateLimiter.getRateLimiterConfig().getLimitForPeriod() != limit) {
			if (limit < 1) {
				logger.warn("{} Invalid maxRequests {} for rate limiter {}, keeping {}", origin, limit, name, rateLimiter.getRateLimiterConfig().getLimitForPeriod());
			} else {
				rateLimiter.changeLimitForPeriod(limit);
			}
		}
		return rateLimiter;
	}

	private static RateLimiter createRateLimiter(String origin, String name, int limit, Duration period) {
		if (limit < 1) {
			logger.warn("{} Invalid maxRequests {} for rate limiter {}, using {}", origin, limit, name, Integer.MAX_VALUE);
			limit = Integer.MAX_VALUE;
		}
		logger.debug("{} Creating rate limiter {} with {} requests every {}", origin, name, limit, validPeriod(origin, period));
		return RateLimiter.of(name, RateLimiterConfig.custom()
			.limitForPeriod(limit)
			.limitRefreshPeriod(validPeriod(origin, period))
			.timeoutDuration(Duration.ZERO)
			.build());
	}

	private static Duration validPeriod(String origin, Duration period) {
		if (period == null) return DEFAULT_PERIOD;
		if (period.isNegative() || period.isZero()) {
			logger.warn("{} Invalid maxRequestsPeriod {}, using {}", origin, period, DEFAULT_PERIOD);
			return DEFAULT_PERIOD;
		}
		return period;
	}

	@Bean
	public Filter rateLimitInterceptor() {
		return new GenericFilterBean() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
				HttpServletRequest httpRequest = (HttpServletRequest) request;
				HttpServletResponse httpResponse = (HttpServletResponse) response;
				var origin = auth.getOrigin();
				if (!getOriginRateLimiter(origin).acquirePermission()) {
					RateLimitConfig.logger.debug("{} Rate limit exceeded for origin: {}", origin, httpRequest.getRequestURI());
					httpResponse.setStatus(429);
					httpResponse.setHeader("X-RateLimit-Limit", ""+getOriginRateLimiter(origin).getRateLimiterConfig().getLimitForPeriod());
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					httpResponse.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
					return;
				}
				if (!httpRateLimiter.acquirePermission()) {
					RateLimitConfig.logger.debug("{} HTTP rate limit exceeded for request: {}", origin, httpRequest.getRequestURI());
					httpResponse.setStatus(429);
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					httpResponse.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
					return;
				}
				try {
					httpBulkhead.executeCheckedSupplier(() -> {
						chain.doFilter(request, response);
						return null;
					});
				} catch (BulkheadFullException e) {
					RateLimitConfig.logger.debug("{} HTTP concurrent limit exceeded for request: {}", origin, httpRequest.getRequestURI());
					httpResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					httpResponse.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
}
