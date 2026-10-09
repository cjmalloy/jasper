package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jasper.client.JasperClient;
import jasper.config.Config.ServerConfig;
import jasper.config.JacksonConfiguration;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

	@Mock
	PluginRepository pluginRepository;

	@Mock
	TemplateRepository templateRepository;

	@Mock
	RefRepository refRepository;

	@Mock
	ExtRepository extRepository;

	@Mock
	UserRepository userRepository;

	@Mock
	IngestPlugin ingestPlugin;

	Ref remote;

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
	}

	@BeforeEach
	void setUp() {
		remote = Ref.from("https://remote.example.com", "", "+plugin/origin", "+plugin/origin/push", "+plugin/origin/pull");
		remote.setTitle("Remote");
		remote.setPlugin("+plugin/origin", Map.of("remote", "@target", "local", "@local"));
		remote.setPlugin("+plugin/origin/pull", Map.of("batchSize", 2));
		when(configs.root()).thenReturn(ServerConfig.builder().build());
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

	FeignException status(int status) {
		var e = mock(FeignException.class);
		when(e.status()).thenReturn(status);
		return e;
	}

	Plugin plugin(String tag, Instant modified) {
		var plugin = new Plugin();
		plugin.setTag(tag);
		plugin.setModified(modified);
		return plugin;
	}

	@Test
	void pullTooLargeEntityIsFatal() {
		var e = status(413);
		when(client.pluginPull(any(URI.class), anyMap())).thenThrow(e);

		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> replicator.pull(remote));

		verify(client, times(2)).pluginPull(any(URI.class), anyMap());
		verify(tagger).attachError(eq(""), eq(remote), startsWith("Fatal error pulling"), any());
	}

	@Test
	void pullClientErrorIsFatal() {
		var e = status(400);
		when(client.pluginPull(any(URI.class), anyMap())).thenThrow(e);

		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> replicator.pull(remote));

		verify(client, times(1)).pluginPull(any(URI.class), anyMap());
		verify(tagger).attachError(eq(""), eq(remote), startsWith("Fatal error pulling"), any());
	}

	@Test
	void pullCursorMustAdvance() {
		var cursor = Instant.parse("2020-01-01T00:00:00Z");
		when(pluginRepository.getCursor("@local")).thenReturn(cursor);
		when(client.pluginPull(any(URI.class), anyMap())).thenReturn(List.of(
			plugin("plugin/a", cursor.minusSeconds(1)),
			plugin("plugin/b", cursor)));

		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> replicator.pull(remote));

		verify(client, times(1)).pluginPull(any(URI.class), anyMap());
		verify(tagger).attachError(eq(""), eq(remote), startsWith("Fatal error pulling"), any());
	}

	@Test
	void pullTooLargeOnEmptyOriginReducesBatchSize() {
		var e = status(413);
		var a = plugin("plugin/a", Instant.parse("2020-01-01T00:00:00Z"));
		when(client.pluginPull(any(URI.class), anyMap()))
			.thenThrow(e)
			.thenReturn(List.of(a))
			.thenReturn(List.of());

		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> replicator.pull(remote));

		verify(client, times(3)).pluginPull(any(URI.class), anyMap());
		verify(ingestPlugin).push(a);
		verify(tagger).attachLogs(eq(""), eq(remote), startsWith("Error replicating entities, reducing batch size to 1"), any());
		verify(tagger, never()).attachError(any(), any(Ref.class), any(), any());
	}
}
