package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import jasper.component.ConfigCache;
import jasper.config.Config.SecurityConfig;
import jasper.config.Config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitConfigTest {

	RateLimitConfig rateLimitConfig(ConfigCache configs) {
		var config = new RateLimitConfig();
		config.configs = configs;
		config.props = new Props();
		config.httpBulkhead = Bulkhead.ofDefaults("test");
		config.init();
		return config;
	}

	@Test
	void testDatabaseUnavailableWhileCreatingOriginRateLimiter() throws Exception {
		var configs = mock(ConfigCache.class);
		when(configs.root()).thenReturn(new ServerConfig());
		when(configs.security(anyString())).thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
			new SQLTransientConnectionException("Hikari - Connection is not available, request timed out after 5000ms.")));
		var request = new MockHttpServletRequest("GET", "/api/v1/ref/page");
		request.addHeader("Local-Origin", "@new");
		var response = new MockHttpServletResponse();

		rateLimitConfig(configs).rateLimitInterceptor().getFilter().doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getHeader("X-RateLimit-Retry-After")).isNotNull();
	}

	@Test
	void testAllowedRequestReachesChain() throws Exception {
		var configs = mock(ConfigCache.class);
		when(configs.root()).thenReturn(new ServerConfig());
		when(configs.security(anyString())).thenReturn(new SecurityConfig());
		var request = new MockHttpServletRequest("GET", "/api/v1/ref/page");
		var response = new MockHttpServletResponse();
		var chain = new MockFilterChain();

		rateLimitConfig(configs).rateLimitInterceptor().getFilter().doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void testOriginConcurrencyLimit() throws Exception {
		var configs = mock(ConfigCache.class);
		when(configs.root()).thenReturn(new ServerConfig());
		when(configs.security(anyString())).thenReturn(new SecurityConfig().withMaxConcurrentRequests(2));
		var filter = rateLimitConfig(configs).rateLimitInterceptor().getFilter();
		var started = new CountDownLatch(2);
		var release = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try {
			var inFlight = new ArrayList<Future<MockHttpServletResponse>>();
			for (var i = 0; i < 2; i++) {
				inFlight.add(pool.submit(() -> {
					var request = new MockHttpServletRequest("GET", "/api/v1/ref/page");
					request.addHeader("Local-Origin", "@busy");
					var response = new MockHttpServletResponse();
					filter.doFilter(request, response, (req, res) -> {
						started.countDown();
						try {
							release.await();
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					});
					return response;
				}));
			}
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

			var busy = new MockHttpServletRequest("GET", "/api/v1/ref/page");
			busy.addHeader("Local-Origin", "@busy");
			var busyResponse = new MockHttpServletResponse();
			filter.doFilter(busy, busyResponse, new MockFilterChain());
			var quiet = new MockHttpServletRequest("GET", "/api/v1/ref/page");
			quiet.addHeader("Local-Origin", "@quiet");
			var quietResponse = new MockHttpServletResponse();
			var quietChain = new MockFilterChain();
			filter.doFilter(quiet, quietResponse, quietChain);

			assertThat(busyResponse.getStatus()).isEqualTo(429);
			assertThat(busyResponse.getHeader("X-RateLimit-Limit")).isEqualTo("2");
			assertThat(busyResponse.getHeader("X-RateLimit-Retry-After")).isNotNull();
			assertThat(quietResponse.getStatus()).isEqualTo(200);
			assertThat(quietChain.getRequest()).isSameAs(quiet);

			release.countDown();
			for (var f : inFlight) assertThat(f.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
			var after = new MockHttpServletRequest("GET", "/api/v1/ref/page");
			after.addHeader("Local-Origin", "@busy");
			var afterResponse = new MockHttpServletResponse();
			filter.doFilter(after, afterResponse, new MockFilterChain());
			assertThat(afterResponse.getStatus()).isEqualTo(200);
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void testOriginConcurrencyReleasedOnError() throws Exception {
		var configs = mock(ConfigCache.class);
		when(configs.root()).thenReturn(new ServerConfig());
		when(configs.security(anyString())).thenReturn(new SecurityConfig().withMaxConcurrentRequests(1));
		var filter = rateLimitConfig(configs).rateLimitInterceptor().getFilter();
		var request = new MockHttpServletRequest("GET", "/api/v1/ref/page");
		request.addHeader("Local-Origin", "@failing");

		assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);
		var response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(200);
	}
}
