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

import static org.assertj.core.api.Assertions.assertThat;
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
}
