package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jasper.client.JasperClient;
import jasper.config.Config.ServerConfig;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplicatorTest {

	@InjectMocks
	Replicator replicator;

	@Mock
	JasperClient client;

	@Mock
	TunnelClient tunnel;

	@Mock
	ConfigCache configs;

	@Mock
	Tagger tagger;

	Ref remote;

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
	}

	@BeforeEach
	void setUp() {
		remote = Ref.from("https://remote.example.com", "", "+plugin/origin", "+plugin/origin/push");
		remote.setTitle("Remote");
		remote.setPlugin("+plugin/origin", Map.of("remote", "@target"));
		when(configs.root()).thenReturn(ServerConfig.builder().build());
		when(tagger.progress(any(), any())).thenReturn(mock(Tagger.Progress.class));
		doAnswer(i -> {
			i.<TunnelClient.ProxyRequest>getArgument(1).go(URI.create(remote.getUrl()));
			return null;
		}).when(tunnel).proxy(eq(remote), any());
	}

	@Test
	void pushAccessDeniedIsLoggedOnRemoteOrigin() {
		when(client.pluginCursor(any(URI.class), eq("@target"))).thenThrow(mock(FeignException.Forbidden.class));

		replicator.push(remote);

		verify(tagger).attachError(eq(""), eq(remote), startsWith("Access denied pushing"), any());
	}
}
