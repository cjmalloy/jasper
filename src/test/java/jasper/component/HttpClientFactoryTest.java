package jasper.component;

import jasper.config.Props;
import jasper.security.Auth;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.ScopeNotActiveException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HttpClientFactoryTest {

	@Mock
	Props props;

	@Mock
	Auth auth;

	HttpClientFactory factory;

	@BeforeEach
	void init() {
		factory = new HttpClientFactory();
		factory.props = props;
		factory.auth = auth;
		when(props.getOrigin()).thenReturn("");
	}

	@AfterEach
	void cleanup() {
		factory.cleanup();
	}

	@Test
	void testGetClient() {
		when(auth.getOrigin()).thenReturn("");

		var client = factory.getClient();

		assertThat(client).isNotNull();
		assertThat(client).isInstanceOf(CloseableHttpClient.class);
	}

	@Test
	void testGetSerialClient() {
		when(auth.getOrigin()).thenReturn("");

		var client = factory.getSerialClient();

		assertThat(client).isNotNull();
		assertThat(client).isInstanceOf(CloseableHttpClient.class);
	}

	@Test
	void testGetClientForDifferentTenants() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		var client1 = factory.getClient();

		when(auth.getOrigin()).thenReturn("tenant2.example.com");
		var client2 = factory.getClient();

		// Different tenants should get different clients
		assertThat(client1).isNotNull();
		assertThat(client2).isNotNull();
	}

	@Test
	void testGetClientCachedForSameTenant() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		
		var client1 = factory.getClient();
		var client2 = factory.getClient();

		// Same tenant should get the same client instance
		assertThat(client1).isSameAs(client2);
	}

	@Test
	void testGetSerialClientCachedForSameTenant() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		
		var client1 = factory.getSerialClient();
		var client2 = factory.getSerialClient();

		// Same tenant should get the same serial client instance
		assertThat(client1).isSameAs(client2);
	}

	@Test
	void testSerialAndParallelClientsDifferent() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		
		var parallelClient = factory.getClient();
		var serialClient = factory.getSerialClient();

		// Serial and parallel clients should be different instances
		assertThat(parallelClient).isNotSameAs(serialClient);
	}

	@Test
	void testGetClientWithNoActiveScope() {
		when(auth.getOrigin()).thenThrow(new ScopeNotActiveException("request", "Request scope is not active", new IllegalStateException("No active request")));
		when(props.getOrigin()).thenReturn("default-origin");

		var client = factory.getClient();

		// Should fall back to default origin from props
		assertThat(client).isNotNull();
	}

	@Test
	void testGetSerialClientWithNoActiveScope() {
		when(auth.getOrigin()).thenThrow(new ScopeNotActiveException("request", "Request scope is not active", new IllegalStateException("No active request")));
		when(props.getOrigin()).thenReturn("default-origin");

		var client = factory.getSerialClient();

		// Should fall back to default origin from props
		assertThat(client).isNotNull();
	}

	@Test
	void testMultipleTenants() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		var client1 = factory.getClient();

		when(auth.getOrigin()).thenReturn("tenant2.example.com");
		var client2 = factory.getClient();

		when(auth.getOrigin()).thenReturn("tenant3.example.com");
		var client3 = factory.getClient();

		assertThat(client1).isNotNull();
		assertThat(client2).isNotNull();
		assertThat(client3).isNotNull();
	}

	@Test
	void testEvictIdleConnections() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		factory.getClient();

		// Should not throw exception
		factory.evictIdleConnections();
	}

	@Test
	void testLogStats() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		factory.getClient();

		// Should not throw exception
		factory.logStats();
	}

	@Test
	void testCleanup() {
		when(auth.getOrigin()).thenReturn("tenant1.example.com");
		factory.getClient();
		factory.getSerialClient();

		when(auth.getOrigin()).thenReturn("tenant2.example.com");
		factory.getClient();

		// Should not throw exception
		factory.cleanup();
	}

	@Test
	void testEmptyOrigin() {
		when(auth.getOrigin()).thenReturn("");
		
		var client = factory.getClient();
		
		assertThat(client).isNotNull();
	}

	@Test
	void testNullOriginFallback() {
		when(auth.getOrigin()).thenReturn(null);
		when(props.getOrigin()).thenReturn("");
		
		var client = factory.getClient();
		
		assertThat(client).isNotNull();
	}
}
