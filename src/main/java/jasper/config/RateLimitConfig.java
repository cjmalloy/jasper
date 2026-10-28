package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
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
import jasper.service.dto.TemplateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static jasper.security.Auth.LOCAL_ORIGIN_HEADER;
import static jasper.security.Auth.resolveOrigin;
import static jasper.config.BulkheadConfiguration.updateBulkheadConfig;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Configuration
public class RateLimitConfig {
	private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

	private static final Duration DEFAULT_PERIOD = new SecurityConfig().getMaxRequestsPeriod();

	@Autowired
	ConfigCache configs;

	@Autowired
	Props props;

	@Autowired
	Bulkhead httpBulkhead;

	private final ConcurrentHashMap<String, RateLimiter> originRateLimiters = new ConcurrentHashMap<>();
	private RateLimiter getOriginRateLimiter(String origin) {
		return originRateLimiters.computeIfAbsent(origin, k -> createRateLimiter(origin, "http-" + origin,
			configs.security(origin).getMaxRequests(),
			configs.security(origin).getMaxRequestsPeriod()));
	}

	private final ConcurrentHashMap<String, Bulkhead> originBulkheads = new ConcurrentHashMap<>();
	/**
	 * Concurrent request limit for the origin, or null when unlimited.
	 */
	private Bulkhead getOriginBulkhead(String origin) {
		var bulkhead = originBulkheads.get(origin);
		if (bulkhead != null) return bulkhead;
		var limit = configs.security(origin).getMaxConcurrentRequests();
		if (limit < 1) return null;
		return originBulkheads.computeIfAbsent(origin, k -> createBulkhead(origin, limit));
	}

	private static Bulkhead createBulkhead(String origin, int limit) {
		logger.debug("{} Creating bulkhead http-{} with {} concurrent requests", origin, origin, limit);
		return Bulkhead.of("http-" + origin, BulkheadConfig.custom()
			.maxConcurrentCalls(limit)
			.maxWaitDuration(Duration.ZERO)
			.build());
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
			// In-flight requests keep and release the bulkhead they acquired
			originBulkheads.keySet().forEach(origin -> originBulkheads.computeIfPresent(origin, (o, b) -> {
				var limit = configs.security(origin).getMaxConcurrentRequests();
				if (limit < 1) return null;
				if (b.getBulkheadConfig().getMaxConcurrentCalls() != limit) {
					logger.debug("{} Updating bulkhead http-{} to {} concurrent requests", origin, origin, limit);
					updateBulkheadConfig(b, limit);
				}
				return b;
			}));
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

	/**
	 * Runs before Spring Security, so rejected requests skip JWT validation and the security filters.
	 * The origin only depends on the Local-Origin header.
	 */
	@Bean
	public FilterRegistrationBean<Filter> rateLimitInterceptor() {
		var registration = new FilterRegistrationBean<Filter>(new GenericFilterBean() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
				HttpServletRequest httpRequest = (HttpServletRequest) request;
				HttpServletResponse httpResponse = (HttpServletResponse) response;
				var origin = resolveOrigin(props, httpRequest.getHeader(LOCAL_ORIGIN_HEADER));
				try {
					// Creating an origin rate limiter loads its config from the database
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
					var originBulkhead = getOriginBulkhead(origin);
					if (originBulkhead != null && !originBulkhead.tryAcquirePermission()) {
						RateLimitConfig.logger.debug("{} Concurrent limit exceeded for origin: {}", origin, httpRequest.getRequestURI());
						httpResponse.setStatus(429);
						httpResponse.setHeader("X-RateLimit-Limit", ""+originBulkhead.getBulkheadConfig().getMaxConcurrentCalls());
						// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
						httpResponse.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
						return;
					}
					// Permits cover the synchronous part of the request, where the database work happens.
					// Streamed bodies (StreamingResponseBody) keep writing after the chain returns, but holding
					// permits for them would let a few slow downloads lock out the origin.
					try {
						httpBulkhead.executeCheckedSupplier(() -> {
							chain.doFilter(request, response);
							return null;
						});
					} finally {
						if (originBulkhead != null) originBulkhead.onComplete();
					}
				} catch (BulkheadFullException e) {
					RateLimitConfig.logger.debug("{} HTTP concurrent limit exceeded for request: {}", origin, httpRequest.getRequestURI());
					httpResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					httpResponse.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
				} catch (Throwable e) {
					if (!dbUnavailable(e) || httpResponse.isCommitted()) {
						if (e instanceof IOException io) throw io;
						if (e instanceof ServletException se) throw se;
						if (e instanceof RuntimeException re) throw re;
						throw new RuntimeException(e);
					}
					// Thrown outside a controller, for example while loading the user during authentication
					RateLimitConfig.logger.warn("{} No database connection available for request: {}", origin, httpRequest.getRequestURI());
					httpResponse.resetBuffer();
					httpResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					// Add random jitter from 3.5 to 4.5 seconds to prevent thundering herd
					httpResponse.setHeader("X-RateLimit-Retry-After", format("%.1f", ThreadLocalRandom.current().nextDouble(3.5, 4.5)));
				}
			}
		});
		registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
		return registration;
	}

	private static boolean dbUnavailable(Throwable e) {
		var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
		for (var t = e; t != null && seen.add(t); t = t.getCause()) {
			if (t instanceof CannotCreateTransactionException || t instanceof SQLTransientConnectionException) return true;
		}
		return false;
	}
}
