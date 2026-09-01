package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.plugin.Tunnel;
import jasper.repository.UserRepository;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.SshException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
	}

	@BeforeEach
	void setUp() {
		remote = Ref.from("https://example.com", "", "+user/test");
		remote.setTitle("Remote");
		remote.setPlugin("+plugin/origin/tunnel", new Tunnel());

		var user = new User();
		user.setTag("+user/test");
		user.setKey(new byte[]{1});
		when(userRepository.findOneByQualifiedTag("+user/test")).thenReturn(Optional.of(user));
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
	void otherSshFailureRemainsFatal() throws Exception {
		var failure = new SshException("No more authentication methods available");
		when(sshClient.connect(anyString(), anyString(), anyInt())).thenThrow(failure);

		try (MockedStatic<SshClient> clients = mockStatic(SshClient.class)) {
			clients.when(SshClient::setUpDefaultClient).thenReturn(sshClient);

			assertThatThrownBy(() -> tunnelClient.proxy(remote, uri -> fail("Proxy request should not run")))
				.isInstanceOf(RuntimeException.class)
				.hasCause(failure);
		}

		verifyNoInteractions(tagger);
	}
}
