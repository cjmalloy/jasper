package jasper.config;

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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

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

	// Map to store per-origin semaphores
	private final ConcurrentHashMap<String, Semaphore> originSemaphores = new ConcurrentHashMap<>();

	private Semaphore getOriginSemaphore(String origin) {
		return originSemaphores.computeIfAbsent(origin, k -> {
			var maxConcurrent = configs.root().getMaxConcurrentRequestsPerOrigin();
			logger.debug("Creating rate limit semaphore for origin {} with {} permits", origin, maxConcurrent);
			return new Semaphore(maxConcurrent);
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
				var semaphore = getOriginSemaphore(origin);
				
				if (!semaphore.tryAcquire()) {
					logger.warn("{} Rate limit exceeded for origin: {}", origin, request.getRequestURI());
					response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE); // 503
					return false;
				}
				
				// Store origin and semaphore in request attributes for cleanup
				request.setAttribute("rate-limit-origin", origin);
				request.setAttribute("rate-limit-semaphore", semaphore);
				return true;
			}

			@Override
			public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
				var semaphore = (Semaphore) request.getAttribute("rate-limit-semaphore");
				if (semaphore != null) {
					semaphore.release();
				}
			}
		};
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor());
	}
}