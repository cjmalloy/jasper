package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.Config.ServerConfig;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.plugin.Origin;
import jasper.plugin.Tunnel;
import jasper.plugin.Tunnel.SftpMode;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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

	Map<String, String> pushed = new HashMap<>();

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
	}

	@BeforeEach
	void setUp() throws Exception {
		replicator = new Replicator();
		replicator.tunnel = tunnel;
		replicator.configs = configs;
		replicator.fileCache = Optional.of(fileCache);

		lenient().when(configs.root()).thenReturn(ServerConfig.builderFor("").build());
		lenient().doAnswer(i -> {
			i.<TunnelClient.SftpRequest>getArgument(1).go(sftp);
			return null;
		}).when(tunnel).sftp(any(), any());
		lenient().doAnswer(i -> {
			pushed.put(i.getArgument(0), new String(i.<InputStream>getArgument(2).readAllBytes()));
			return null;
		}).when(fileCache).push(any(), eq(""), any(InputStream.class));

		remote = Ref.from("https://example.com", "", "+user/test", "+plugin/origin/pull");
		remote.setPlugin("+plugin/origin", new Origin());
		mode(SftpMode.CACHE);
	}

	void mode(SftpMode mode) {
		var tunnelConfig = new Tunnel();
		tunnelConfig.setSftp(mode);
		remote.setPlugin("+plugin/origin/tunnel", tunnelConfig);
	}

	@Test
	void parseModes() throws Exception {
		var om = new ObjectMapper();
		assertThat(om.readValue("{\"sftp\":\"off\"}", Tunnel.class).getSftp()).isEqualTo(SftpMode.OFF);
		assertThat(om.readValue("{\"sftp\":\"stream\"}", Tunnel.class).getSftp()).isEqualTo(SftpMode.STREAM);
		assertThat(om.readValue("{\"sftp\":\"cache\"}", Tunnel.class).getSftp()).isEqualTo(SftpMode.CACHE);
		assertThat(om.readValue("{\"sftp\":\"sync\"}", Tunnel.class).getSftp()).isEqualTo(SftpMode.SYNC);
		assertThat(om.writeValueAsString(new Tunnel())).doesNotContain("sftp");
	}

	@Test
	void fetchCacheOverSftp() throws Exception {
		when(sftp.read("cache/abc")).thenReturn(new ByteArrayInputStream("cached".getBytes()));
		when(fileCache.fetch("cache:abc", "")).thenReturn(new ByteArrayInputStream("cached".getBytes()));

		try (var res = replicator.fetch("cache:abc", remote)) {
			assertThat(new String(res.getInputStream().readAllBytes())).isEqualTo("cached");
		}

		assertThat(pushed).containsEntry("cache:abc", "cached");
		verify(tunnel, never()).proxy(any(), any());
	}

	@Test
	void fetchCacheOverSftpInSyncMode() throws Exception {
		mode(SftpMode.SYNC);
		when(sftp.read("cache/abc")).thenReturn(new ByteArrayInputStream("cached".getBytes()));
		when(fileCache.fetch("cache:abc", "")).thenReturn(new ByteArrayInputStream("cached".getBytes()));

		assertThat(replicator.fetch("cache:abc", remote)).isNotNull();

		assertThat(pushed).containsEntry("cache:abc", "cached");
		verify(tunnel, never()).proxy(any(), any());
	}

	@Test
	void fetchCacheFallsBackToHttp() throws Exception {
		doThrow(new IOException("No such file")).when(tunnel).sftp(eq(remote), any());

		assertThat(replicator.fetch("cache:abc", remote)).isNull();

		assertThat(pushed).isEmpty();
		verify(tunnel).proxy(eq(remote), any());
	}

	@Test
	void fetchInvalidCacheIdSkipsSftp() throws Exception {
		assertThat(replicator.fetch("cache:../abc", remote)).isNull();

		verify(tunnel, never()).sftp(any(), any());
		verify(tunnel).proxy(eq(remote), any());
	}

	@Test
	void fetchUsesHttpWhenOffOrStream() throws Exception {
		for (var mode : new SftpMode[]{ null, SftpMode.OFF, SftpMode.STREAM }) {
			mode(mode);
			assertThat(replicator.fetch("cache:abc", remote)).isNull();
		}

		verify(tunnel, never()).sftp(any(), any());
	}

	@Test
	void fetchNonCacheUrlUsesHttp() throws Exception {
		assertThat(replicator.fetch("https://example.com/file", remote)).isNull();

		verify(tunnel, never()).sftp(any(), any());
		verify(tunnel).proxy(eq(remote), any());
	}

	@Test
	void sftpStreamInStreamMode() throws Exception {
		mode(SftpMode.STREAM);
		when(tunnel.sftpStream(remote, "cache/abc")).thenReturn(new ByteArrayInputStream("cached".getBytes()));

		try (var is = replicator.sftpStream("cache:abc", remote)) {
			assertThat(new String(is.readAllBytes())).isEqualTo("cached");
		}
		assertThat(pushed).isEmpty();
	}

	@Test
	void sftpStreamFailureReturnsNull() throws Exception {
		mode(SftpMode.STREAM);
		when(tunnel.sftpStream(remote, "cache/abc")).thenThrow(new IOException("No such file"));

		assertThat(replicator.sftpStream("cache:abc", remote)).isNull();
	}

	@Test
	void sftpStreamOnlyInStreamMode() throws Exception {
		for (var mode : new SftpMode[]{ null, SftpMode.OFF, SftpMode.CACHE, SftpMode.SYNC }) {
			mode(mode);
			assertThat(replicator.sftpStream("cache:abc", remote)).isNull();
		}
		mode(SftpMode.STREAM);
		assertThat(replicator.sftpStream("cache:../abc", remote)).isNull();
		assertThat(replicator.sftpStream("https://example.com/file", remote)).isNull();

		verify(tunnel, never()).sftpStream(any(), any());
	}

	SftpClient.DirEntry entry(String name, boolean file, Instant modified) {
		var attrs = new SftpClient.Attributes();
		attrs.setPermissions((file ? SftpConstants.S_IFREG : SftpConstants.S_IFDIR) | 0644);
		attrs.setModifyTime(FileTime.from(modified));
		return new SftpClient.DirEntry(name, name, attrs);
	}

	@Test
	void syncCopiesNewSettledFiles() throws Exception {
		mode(SftpMode.SYNC);
		var old = Instant.now().minusSeconds(60);
		when(sftp.readDir("cache")).thenReturn(List.of(
			entry("new", true, old),
			entry("existing", true, old),
			entry("writing", true, Instant.now()),
			entry("dir", false, old),
			entry("bad.name", true, old)));
		when(fileCache.cacheExists("cache:new", "")).thenReturn(false);
		when(fileCache.cacheExists("cache:existing", "")).thenReturn(true);
		when(sftp.read("cache/new")).thenReturn(new ByteArrayInputStream("new".getBytes()));

		replicator.sftpSync(remote);

		assertThat(pushed).containsOnlyKeys("cache:new").containsEntry("cache:new", "new");
	}

	@Test
	void syncWithoutRemoteCacheFolder() throws Exception {
		mode(SftpMode.SYNC);
		when(sftp.readDir("cache")).thenThrow(new SftpException(SftpConstants.SSH_FX_NO_SUCH_FILE, "No such file"));

		replicator.sftpSync(remote);

		assertThat(pushed).isEmpty();
	}

	@Test
	void syncOnlyInSyncMode() throws Exception {
		for (var mode : new SftpMode[]{ null, SftpMode.OFF, SftpMode.STREAM, SftpMode.CACHE }) {
			mode(mode);
			replicator.sftpSync(remote);
		}

		verify(tunnel, never()).sftp(any(), any());
	}
}
