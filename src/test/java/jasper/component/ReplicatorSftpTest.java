package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.Config.ServerConfig;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.plugin.Origin;
import jasper.plugin.Tunnel;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplicatorSftpTest {

	Replicator replicator;

	@Mock
	TunnelClient tunnel;

	@Mock
	ConfigCache configs;

	@Mock
	FileCache fileCache;

	@Mock
	SftpClient sftp;

	Ref remote;

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
	}

	@BeforeEach
	void setUp() {
		replicator = new Replicator();
		replicator.tunnel = tunnel;
		replicator.configs = configs;
		replicator.fileCache = Optional.of(fileCache);

		var root = ServerConfig.builderFor("").build();
		when(configs.root()).thenReturn(root);

		var tunnelConfig = new Tunnel();
		tunnelConfig.setSftp(true);
		remote = Ref.from("https://example.com", "", "+user/test", "+plugin/origin/pull");
		remote.setPlugin("+plugin/origin", new Origin());
		remote.setPlugin("+plugin/origin/tunnel", tunnelConfig);
	}

	@Test
	void fetchCacheOverSftp() throws Exception {
		doAnswer(i -> {
			i.<TunnelClient.SftpRequest>getArgument(1).go(sftp);
			return null;
		}).when(tunnel).sftp(eq(remote), any());
		when(sftp.read("cache/abc")).thenReturn(new ByteArrayInputStream("cached".getBytes()));
		var pushed = new String[1];
		doAnswer(i -> {
			pushed[0] = new String(i.<InputStream>getArgument(2).readAllBytes());
			return null;
		}).when(fileCache).push(eq("cache:abc"), eq(""), any(InputStream.class));
		when(fileCache.fetch("cache:abc", "")).thenReturn(new ByteArrayInputStream("cached".getBytes()));

		try (var res = replicator.fetch("cache:abc", remote)) {
			assertThat(new String(res.getInputStream().readAllBytes())).isEqualTo("cached");
		}

		assertThat(pushed[0]).isEqualTo("cached");
		verify(tunnel, never()).proxy(any(), any());
	}

	@Test
	void fetchCacheFallsBackToHttp() throws Exception {
		doThrow(new IOException("No such file")).when(tunnel).sftp(eq(remote), any());

		assertThat(replicator.fetch("cache:abc", remote)).isNull();

		verify(fileCache, never()).push(any(), any(), any(InputStream.class));
		verify(tunnel).proxy(eq(remote), any());
	}

	@Test
	void fetchInvalidCacheIdSkipsSftp() throws Exception {
		assertThat(replicator.fetch("cache:../abc", remote)).isNull();

		verify(tunnel, never()).sftp(any(), any());
		verify(tunnel).proxy(eq(remote), any());
	}

	@Test
	void fetchWithoutSftpUsesHttp() throws Exception {
		remote.setPlugin("+plugin/origin/tunnel", new Tunnel());

		assertThat(replicator.fetch("cache:abc", remote)).isNull();

		verify(tunnel, never()).sftp(any(), any());
		verify(tunnel).proxy(eq(remote), any());
	}

	@Test
	void fetchNonCacheUrlUsesHttp() throws Exception {
		assertThat(replicator.fetch("https://example.com/file", remote)).isNull();

		verify(tunnel, never()).sftp(any(), any());
		verify(tunnel).proxy(eq(remote), any());
	}
}
