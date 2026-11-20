package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.JacksonConfiguration;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.InvalidTunnelException;
import jasper.plugin.Tunnel;
import jasper.repository.UserRepository;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TunnelClientSftpTest {

	@InjectMocks
	TunnelClient tunnelClient;

	@Mock
	TaskScheduler taskScheduler;

	@Mock
	UserRepository userRepository;

	@Mock
	Tagger tagger;

	@TempDir
	Path storage;

	SshServer server;

	Ref remote;

	@BeforeAll
	static void setUpJackson() {
		ReflectionTestUtils.setField(JacksonConfiguration.class, "om", new ObjectMapper());
	}

	@BeforeEach
	void setUp() throws Exception {
		KeyPair keyPair = KeyUtils.generateKeyPair("ssh-ed25519", 256);
		var key = new ByteArrayOutputStream();
		OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, "", null, key);

		server = SshServer.setUpDefaultServer();
		server.setHost("localhost");
		server.setPort(0);
		server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(storage.resolve("host_key")));
		server.setPublickeyAuthenticator((username, publicKey, session) ->
			username.equals("user_test") && KeyUtils.compareKeys(publicKey, keyPair.getPublic()));
		server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
		server.setFileSystemFactory(new VirtualFileSystemFactory(storage.resolve("root")));
		server.start();

		Files.createDirectories(storage.resolve("root/cache"));
		Files.writeString(storage.resolve("root/cache/abc"), "cached");

		var tunnel = new Tunnel();
		tunnel.setSftp(Tunnel.SftpMode.CACHE);
		tunnel.setSshHost("localhost");
		tunnel.setSshPort(server.getPort());
		remote = Ref.from("https://example.com", "", "+user/test");
		remote.setTitle("Remote");
		remote.setPlugin("+plugin/origin/tunnel", tunnel);

		var user = new User();
		user.setTag("+user/test");
		user.setKey(key.toByteArray());
		lenient().when(userRepository.findOneByQualifiedTag("+user/test")).thenReturn(Optional.of(user));
	}

	@AfterEach
	void tearDown() throws Exception {
		tunnelClient.tunnels.values().forEach(t -> t.client().stop());
		server.stop(true);
	}

	@Test
	void sftpReadsCacheFile() throws Exception {
		var result = new String[1];

		tunnelClient.sftp(remote, sftp -> {
			try (var is = sftp.read("cache/abc")) {
				result[0] = new String(is.readAllBytes());
			}
		});

		assertThat(result[0]).isEqualTo("cached");
	}

	@Test
	void sftpStreamReadsCacheFileAndReleasesTunnel() throws Exception {
		try (var is = tunnelClient.sftpStream(remote, "cache/abc")) {
			assertThat(new String(is.readAllBytes())).isEqualTo("cached");
			assertThat(tunnelClient.tunnels.values()).singleElement()
				.satisfies(t -> assertThat(t.connections()).isEqualTo(1));
		}
		assertThat(tunnelClient.tunnels.values()).singleElement()
			.satisfies(t -> assertThat(t.connections()).isEqualTo(0));
	}

	@Test
	void sftpStreamMissingFileReleasesTunnel() {
		assertThatThrownBy(() -> tunnelClient.sftpStream(remote, "cache/missing"))
			.isInstanceOf(IOException.class);
		assertThat(tunnelClient.tunnels.values()).singleElement()
			.satisfies(t -> assertThat(t.connections()).isEqualTo(0));
	}

	@Test
	void sftpDisabledThrows() {
		remote.setPlugin("+plugin/origin/tunnel", new Tunnel());
		assertThatThrownBy(() -> tunnelClient.sftp(remote, sftp -> {}))
			.isInstanceOf(InvalidTunnelException.class);
	}

	@Test
	void sftpListsCacheFiles() throws Exception {
		Files.createDirectories(storage.resolve("root/cache/dir"));
		var entries = new HashMap<String, SftpClient.Attributes>();

		tunnelClient.sftp(remote, sftp -> sftp.readDir("cache").forEach(e -> entries.put(e.getFilename(), e.getAttributes())));

		assertThat(entries.get("abc").isRegularFile()).isTrue();
		assertThat(entries.get("abc").getModifyTime()).isNotNull();
		assertThat(entries.get("dir").isRegularFile()).isFalse();
	}

	@Test
	void sftpMissingFileThrows() {
		assertThatThrownBy(() -> tunnelClient.sftp(remote, sftp -> sftp.read("cache/missing").close()))
			.isInstanceOf(IOException.class);
	}
}
