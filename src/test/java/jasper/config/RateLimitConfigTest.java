package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import jasper.component.ConfigCache;
import jasper.security.Auth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link RateLimitConfig} class.
 */
class RateLimitConfigTest {

	@Mock
	private ConfigCache configs;

	@Mock
	private Auth auth;

	private Bulkhead httpBulkhead;

	private RateLimitConfig rateLimitConfig;

	private HandlerInterceptor rateLimitInterceptor;

	@BeforeEach
	public void setup() {
		configs = mock(ConfigCache.class);
		auth = mock(Auth.class);

		// Create a real bulkhead with max 2 concurrent requests for testing
		BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
		httpBulkhead = registry.bulkhead("test-http", BulkheadConfig.custom()
			.maxConcurrentCalls(2)
			.maxWaitDuration(java.time.Duration.ofSeconds(0))
			.build());

		rateLimitConfig = new RateLimitConfig();
		rateLimitConfig.configs = configs;
		rateLimitConfig.auth = auth;
		rateLimitConfig.httpBulkhead = httpBulkhead;

		// Setup default mocks
		when(auth.getOrigin()).thenReturn("");
		var security = new Config.SecurityConfig();
		security.setMaxRequests(10);
		when(configs.security(anyString())).thenReturn(security);

		rateLimitInterceptor = rateLimitConfig.rateLimitInterceptor();
	}

	@Test
	void shouldAcquireAndCompletePermitForSuccessfulRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		// Initial state - should have 2 permits available
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);

		// preHandle should acquire a permit
		boolean result = rateLimitInterceptor.preHandle(request, response, null);
		assertThat(result).isTrue();
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);

		// afterCompletion should complete the permit (calls onComplete())
		rateLimitInterceptor.afterCompletion(request, response, null, null);
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
	}

	@Test
	void shouldRejectRequestWhenGlobalRateLimitHit() throws Exception {
		MockHttpServletRequest request1 = new MockHttpServletRequest();
		MockHttpServletResponse response1 = new MockHttpServletResponse();
		MockHttpServletRequest request2 = new MockHttpServletRequest();
		MockHttpServletResponse response2 = new MockHttpServletResponse();
		MockHttpServletRequest request3 = new MockHttpServletRequest();
		MockHttpServletResponse response3 = new MockHttpServletResponse();

		// Initial state - should have 2 permits available
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);

		// First request should succeed
		rateLimitInterceptor.preHandle(request1, response1, null);
		assertThat(response1.getStatus()).isEqualTo(200);
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);

		// Second request should succeed
		rateLimitInterceptor.preHandle(request2, response2, null);
		assertThat(response2.getStatus()).isEqualTo(200);
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(0);

		// Third request should be rejected with 503 (global rate limit hit)
		boolean result = rateLimitInterceptor.preHandle(request3, response3, null);
		assertThat(result).isFalse();
		assertThat(response3.getStatus()).isEqualTo(503);
		// Permits should still be 0 (no permit was acquired or released)
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(0);

		// Complete the first two requests (afterCompletion is only called for successful preHandle)
		rateLimitInterceptor.afterCompletion(request1, response1, null, null);
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);

		rateLimitInterceptor.afterCompletion(request2, response2, null, null);
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
	}

	@Test
	void shouldReleasePermitWhenOriginRateLimitHit() throws Exception {
		// Setup origin rate limiter to reject immediately
		var security = new Config.SecurityConfig();
		security.setMaxRequests(0); // Set to 0 to reject all requests
		when(configs.security(anyString())).thenReturn(security);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		// Initial state - should have 2 permits available
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);

		// preHandle should acquire global permit but then release it when origin rate limit is hit
		boolean result = rateLimitInterceptor.preHandle(request, response, null);
		assertThat(result).isFalse();
		assertThat(response.getStatus()).isEqualTo(429); // Origin rate limit
		// Permit should be released already in preHandle
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
	}

	@Test
	void shouldHandleMultipleSequentialRequests() throws Exception {
		// Simulate multiple requests coming in and completing
		for (int i = 0; i < 10; i++) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			MockHttpServletResponse response = new MockHttpServletResponse();

			// Initial state should always be 2 permits
			assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);

			// Acquire permit
			boolean result = rateLimitInterceptor.preHandle(request, response, null);
			assertThat(result).isTrue();
			assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);

			// Complete permit
			rateLimitInterceptor.afterCompletion(request, response, null, null);
			assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
		}
	}

	@Test
	void shouldNotExceedMaxPermits() throws Exception {
		MockHttpServletRequest request1 = new MockHttpServletRequest();
		MockHttpServletResponse response1 = new MockHttpServletResponse();
		MockHttpServletRequest request2 = new MockHttpServletRequest();
		MockHttpServletResponse response2 = new MockHttpServletResponse();
		MockHttpServletRequest request3 = new MockHttpServletRequest();
		MockHttpServletResponse response3 = new MockHttpServletResponse();

		// Take both available permits
		rateLimitInterceptor.preHandle(request1, response1, null);
		rateLimitInterceptor.preHandle(request2, response2, null);
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(0);

		// Try to acquire another - should fail
		boolean result = rateLimitInterceptor.preHandle(request3, response3, null);
		assertThat(result).isFalse();
		assertThat(response3.getStatus()).isEqualTo(503);

		// Complete only the two successful requests (afterCompletion not called for request3)
		rateLimitInterceptor.afterCompletion(request1, response1, null, null);
		rateLimitInterceptor.afterCompletion(request2, response2, null, null);

		// Final state should be exactly 2 permits
		assertThat(httpBulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
	}
}
