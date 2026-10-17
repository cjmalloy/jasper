package jasper.component;

import jasper.IntegrationTest;
import jasper.client.JasperClient;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.repository.RefRepository;
import jasper.repository.UserRepository;
import jasper.service.dto.RefReplDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.AopTestUtils.getTargetObject;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@IntegrationTest
public class ReplicatorPushIT {

	@Autowired
	Replicator replicator;

	@Autowired
	RefRepository refRepository;

	@Autowired
	UserRepository userRepository;

	JasperClient client;
	Object originalClient;
	Object originalTunnel;
	List<String> pushed;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		userRepository.deleteAll();
		var target = getTargetObject(replicator);
		originalClient = getField(target, "client");
		originalTunnel = getField(target, "tunnel");
		client = mock(JasperClient.class);
		pushed = new ArrayList<>();
		doAnswer(i -> {
			for (var ref : i.<List<RefReplDto>>getArgument(2)) pushed.add(ref.getUrl());
			return null;
		}).when(client).refPush(any(URI.class), anyString(), any());
		var tunnel = mock(TunnelClient.class);
		doAnswer(i -> {
			i.<TunnelClient.ProxyRequest>getArgument(1).go(URI.create("https://remote.example.com"));
			return null;
		}).when(tunnel).proxy(any(), any());
		setField(target, "client", client);
		setField(target, "tunnel", tunnel);
		ref("https://example.com/public", "public");
		ref("https://example.com/private", "_secret");
		ref("https://example.com/alice", "+user/alice");
	}

	@AfterEach
	void cleanup() {
		var target = getTargetObject(replicator);
		setField(target, "client", originalClient);
		setField(target, "tunnel", originalTunnel);
		refRepository.deleteAll();
		userRepository.deleteAll();
	}

	void ref(String url, String ...tags) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setTags(new ArrayList<>(List.of(tags)));
		refRepository.save(ref);
	}

	void user(String tag, String role) {
		var user = new User();
		user.setTag(tag);
		user.setRole(role);
		userRepository.save(user);
	}

	Ref remote(String ...signers) {
		var tags = new ArrayList<>(List.of("+plugin/origin", "+plugin/origin/push"));
		tags.addAll(List.of(signers));
		var remote = Ref.from("https://remote.example.com", "", tags.toArray(String[]::new));
		remote.setTitle("Remote");
		remote.setPlugin("+plugin/origin", Map.of("remote", "@target"));
		return remote;
	}

	@Test
	void testPushOnlyWhatSigningUserCanSee() {
		user("+user/alice", "ROLE_USER");

		replicator.push(remote("+user/alice"));

		assertThat(pushed)
			.containsExactlyInAnyOrder("https://example.com/public", "https://example.com/alice");
	}

	@Test
	void testPushUnsignedOnlyPublic() {
		replicator.push(remote());

		assertThat(pushed)
			.containsExactly("https://example.com/public");
	}

	@Test
	void testPushModSignerEverything() {
		user("+user/mod", "ROLE_MOD");

		replicator.push(remote("+user/mod"));

		assertThat(pushed)
			.containsExactlyInAnyOrder("https://example.com/public", "https://example.com/private", "https://example.com/alice");
	}

	@Test
	void testPushMultipleSignersUnion() {
		user("+user/alice", "ROLE_USER");
		user("+user/bob", "ROLE_USER");

		replicator.push(remote("+user/bob", "+user/alice"));

		assertThat(pushed)
			.containsExactlyInAnyOrder("https://example.com/public", "https://example.com/alice");
	}

	@Test
	void testPushBannedSignerPushesNothing() {
		user("+user/banned", "ROLE_BANNED");

		replicator.push(remote("+user/banned"));

		verify(client, never()).refPush(any(URI.class), anyString(), any());
		verify(client, never()).pluginPush(any(URI.class), anyString(), any());
		assertThat(pushed).isEmpty();
	}
}
