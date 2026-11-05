package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.TunnelCooldownException;
import jasper.plugin.Tunnel;
import jasper.repository.UserRepository;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.nio.channels.UnresolvedAddressException;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static jasper.util.Crypto.writeRsaPrivatePem;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_BY_APPLICATION;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_CONNECTION_LOST;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_HOST_KEY_NOT_VERIFIABLE;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_HOST_NOT_ALLOWED_TO_CONNECT;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_ILLEGAL_USER_NAME;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_KEY_EXCHANGE_FAILED;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE;
import static org.apache.sshd.common.SshConstants.SSH2_DISCONNECT_TOO_MANY_CONNECTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TunnelClientTest {

	@InjectMocks
	TunnelClient tunnelClient;

	@Mock
	TaskScheduler taskScheduler;

	@Mock
	UserRepository userRepository;

	@Mock
	Tagger tagger;

	@Mock
	SshClient sshClient;

	Ref remote;

	static byte[] key;

	@BeforeAll
	static void setUpJackson() throws Exception {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
		var kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		key = writeRsaPrivatePem(kpg.generateKeyPair().getPrivate()).getBytes();
	}

	@BeforeEach
	void setUp() {
		remote = Ref.from("https://example.com", "", "+user/test");
		remote.setTitle("Remote");
		remote.setPlugin("+plugin/origin/tunnel", new Tunnel());

		var user = new User();
		user.setTag("+user/test");
		user.setKey(key);
		lenient().when(userRepository.findOneByQualifiedTag("+user/test")).thenReturn(Optional.of(user));
	}

	@Test
	void brokenPipeAddsLogWithoutError() throws Exception {
		var failure = new SshException("[ssh-connection]: Failed (IOException) to execute: Broken pipe",
			new SshException("[ssh-connection]: Failed (IOException) to execute: Broken pipe",
				new IOException("Broken pipe")));
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenThrow(failure);

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);

			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
		}

		verify(tagger).attachLogs(
			eq(remote.getUrl()),
			eq(remote.getOrigin()),
			contains("Error creating SSH tunnel"),
			contains("Broken pipe"));
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void sessionClosingAddsLogWithoutError() throws Exception {
		var failure = new SshException("Session is being closed");
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenThrow(failure);

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);

			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
		}

		verify(tagger).attachLogs(
			eq(remote.getUrl()),
			eq(remote.getOrigin()),
			contains("Error creating SSH tunnel"),
			contains("Session is being closed"));
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void connectionRefusedAddsLogWithoutError() throws Exception {
		var failure = new SshException("DefaultConnectFuture[test@example.com/127.0.0.1:22]: Failed (ConnectException) to execute: Connection refused",
			new ConnectException("Connection refused"));
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenThrow(failure);

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);

			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
			assertThatThrownBy(() -> tunnelClient.reserveProxy(remote))
				.isInstanceOf(TunnelCooldownException.class);
		}

		verify(tagger).attachLogs(
			eq(remote.getUrl()),
			eq(remote.getOrigin()),
			contains("Error creating SSH tunnel"),
			contains("Connection refused"));
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void retryableFailureStartsCooldown() throws Exception {
		var failure = new SshException("Session is being closed");
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenThrow(failure);

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);

			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
			assertThatThrownBy(() -> tunnelClient.reserveProxy(remote))
				.isInstanceOf(TunnelCooldownException.class);

			clients.verify(SshClient::setUpDefaultClient, times(1));
		}

		verify(sshClient, times(1)).connect(anyString(), anyString(), anyInt());
		verify(tagger, times(1)).attachLogs(anyString(), anyString(), anyString(), anyString());
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void overlappingCallsShareCooldown() throws Exception {
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			started.countDown();
			release.await(10, TimeUnit.SECONDS);
			throw new SshException("Session is being closed");
		});
		Runnable call = () -> {
			try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
				clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);
				tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
			}
		};

		var first = new Thread(call);
		first.start();
		assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
		var second = new Thread(call);
		second.start();
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (second.getState() != Thread.State.BLOCKED && second.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		release.countDown();
		first.join(10_000);
		second.join(10_000);

		verify(sshClient, times(1)).connect(anyString(), anyString(), anyInt());
		verify(tagger, times(1)).attachLogs(anyString(), anyString(), anyString(), anyString());
		verify(tagger, never()).attachError(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void expiredCooldownRetries() throws Exception {
		var failure = new SshException("Session is being closed");
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenThrow(failure);

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);

			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
			tunnelClient.cooldowns.replaceAll((k, v) -> Instant.now().minusSeconds(1));
			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
		}

		verify(sshClient, times(2)).connect(anyString(), anyString(), anyInt());
		verify(tagger, times(2)).attachLogs(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void authenticationFailureIsFatal() throws Exception {
		var failure = new SshException(SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE, "No more authentication methods available");
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenThrow(failure);

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);

			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));
		}

		verify(tagger).attachError(
			eq(remote.getUrl()),
			eq(remote.getOrigin()),
			contains("Fatal error creating SSH tunnel"),
			anyString());
		verify(tagger, never()).attachLogs(anyString(), anyString(), anyString(), anyString());
		assertThat(tunnelClient.cooldowns).isEmpty();
	}

	@Test
	void requestErrorIsNotTunnelError() throws Exception {
		var client = mock(SshClient.class, RETURNS_DEEP_STUBS);
		when(client.connect(anyString(), anyString(), anyInt()).verify(anyLong(), any(TimeUnit.class)).getSession()
			.createLocalPortForwardingTracker(anyInt(), any(SshdSocketAddress.class)).getBoundAddress().getPort())
			.thenReturn(12345);
		var failure = new IllegalStateException("Request failed");

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(client);

			assertThatThrownBy(() -> tunnelClient.proxy(remote, uri -> {
				assertThat(uri.getPort()).isEqualTo(12345);
				throw failure;
			})).isSameAs(failure);
		}

		verifyNoInteractions(tagger);
	}

	@Test
	void invalidPrivateKeyIsFatal() {
		userRepository.findOneByQualifiedTag("+user/test").orElseThrow().setKey(new byte[]{1});

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			tunnelClient.proxy(remote, uri -> fail("Proxy request should not run"));

			clients.verifyNoInteractions();
		}

		verify(tagger).attachError(
			eq(remote.getUrl()),
			eq(remote.getOrigin()),
			contains("Fatal error creating SSH tunnel"),
			contains("private key"));
		assertThat(tunnelClient.cooldowns).isEmpty();
	}

	static Stream<Arguments> failures() {
		return Stream.of(
			Arguments.of(new SshException("DefaultConnectFuture[test@example.com/127.0.0.1:22]: Failed (ConnectException) to execute: Connection refused",
				new ConnectException("Connection refused")), false),
			Arguments.of(new SshException("Failed (UnresolvedAddressException) to execute: null",
				new UnresolvedAddressException()), false),
			Arguments.of(new SshException("Failed (NoRouteToHostException) to execute: No route to host",
				new NoRouteToHostException("No route to host")), false),
			Arguments.of(new SshException("Failed to get operation result within specified timeout: 30000 msec",
				new TimeoutException("Timed out after 30000 msec")), false),
			Arguments.of(new SshException("Session is being closed"), false),
			Arguments.of(new SshException(SSH2_DISCONNECT_CONNECTION_LOST, "Connection lost"), false),
			Arguments.of(new SshException(SSH2_DISCONNECT_TOO_MANY_CONNECTIONS, "Too many connections"), false),
			Arguments.of(new SshException(SSH2_DISCONNECT_BY_APPLICATION, "Server restarting"), false),
			Arguments.of(new SshException(SSH2_DISCONNECT_HOST_NOT_ALLOWED_TO_CONNECT, "Host not allowed to connect"), false),
			Arguments.of(new IOException("Connection reset by peer"), false),
			Arguments.of(new SshException(SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE, "No more authentication methods available"), true),
			Arguments.of(new SshException("No more authentication methods available",
				new SshException(SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE, "No more authentication methods available")), true),
			Arguments.of(new SshException(SSH2_DISCONNECT_HOST_KEY_NOT_VERIFIABLE, "Server key did not validate"), true),
			Arguments.of(new SshException(SSH2_DISCONNECT_KEY_EXCHANGE_FAILED, "Unable to negotiate key exchange"), true),
			Arguments.of(new SshException(SSH2_DISCONNECT_ILLEGAL_USER_NAME, "Illegal user name"), true),
			Arguments.of(new IllegalStateException("Bug"), true));
	}

	@ParameterizedTest
	@MethodSource("failures")
	void classifiesFailures(Exception failure, boolean fatal) {
		assertThat(TunnelClient.isFatal(failure)).isEqualTo(fatal);
	}
}
