package jasper.component;

import jasper.IntegrationTest;
import jasper.client.JasperClient;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.repository.RefRepository;
import jasper.repository.UserRepository;
import jasper.domain.External;
import jasper.service.dto.RefReplDto;
import jasper.service.dto.UserDto;
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
	List<RefReplDto> pushedRefs;
	List<UserDto> pushedUsers;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		userRepository.deleteAll();
		var target = getTargetObject(replicator);
		originalClient = getField(target, "client");
		originalTunnel = getField(target, "tunnel");
		client = mock(JasperClient.class);
		pushed = new ArrayList<>();
		pushedRefs = new ArrayList<>();
		pushedUsers = new ArrayList<>();
		doAnswer(i -> {
			for (var ref : i.<List<RefReplDto>>getArgument(2)) pushed.add(ref.getUrl());
			pushedRefs.addAll(i.getArgument(2));
			return null;
		}).when(client).refPush(any(URI.class), anyString(), any());
		doAnswer(i -> {
			pushedUsers.addAll(i.getArgument(2));
			return null;
		}).when(client).userPush(any(URI.class), anyString(), any());
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

	RefReplDto pushedRef(String url) {
		return pushedRefs.stream().filter(r -> r.getUrl().equals(url)).findFirst().orElseThrow();
	}

	UserDto pushedUser(String tag) {
		return pushedUsers.stream().filter(u -> u.getTag().equals(tag)).findFirst().orElseThrow();
	}

	@Test
	void testPushFiltersHiddenTagsAndPlugins() {
		user("+user/alice", "ROLE_USER");
		var ref = new Ref();
		ref.setUrl("https://example.com/mixed");
		ref.setTags(new ArrayList<>(List.of("public", "_secret", "+user/alice", "_plugin/hidden", "plugin/shown")));
		ref.setPlugin("_plugin/hidden", Map.of("a", 1));
		ref.setPlugin("plugin/shown", Map.of("b", 2));
		refRepository.save(ref);

		replicator.push(remote("+user/alice"));

		var pushedRef = pushedRef("https://example.com/mixed");
		assertThat(pushedRef.getTags())
			.containsExactlyInAnyOrder("public", "+user/alice", "plugin/shown");
		assertThat(pushedRef.getPlugins().has("plugin/shown")).isTrue();
		assertThat(pushedRef.getPlugins().has("_plugin/hidden")).isFalse();
	}

	@Test
	void testPushModKeepsHiddenTagsAndPlugins() {
		user("+user/mod", "ROLE_MOD");
		var ref = new Ref();
		ref.setUrl("https://example.com/mixed");
		ref.setTags(new ArrayList<>(List.of("public", "_secret", "_plugin/hidden")));
		ref.setPlugin("_plugin/hidden", Map.of("a", 1));
		refRepository.save(ref);

		replicator.push(remote("+user/mod"));

		var pushedRef = pushedRef("https://example.com/mixed");
		assertThat(pushedRef.getTags())
			.containsExactlyInAnyOrder("public", "_secret", "_plugin/hidden");
		assertThat(pushedRef.getPlugins().has("_plugin/hidden")).isTrue();
	}

	@Test
	void testPushFiltersUserData() {
		user("+user/alice", "ROLE_USER");
		var bob = new User();
		bob.setTag("+user/bob");
		bob.setReadAccess(new ArrayList<>(List.of("public", "_secret")));
		bob.setWriteAccess(new ArrayList<>(List.of("news", "_secret")));
		var external = new External();
		external.setIds(new ArrayList<>(List.of("bob@example.com")));
		bob.setExternal(external);
		userRepository.save(bob);

		replicator.push(remote("+user/alice"));

		var pushedBob = pushedUser("+user/bob");
		assertThat(pushedBob.getReadAccess()).containsExactly("public");
		assertThat(pushedBob.getWriteAccess()).containsExactly("news");
		assertThat(pushedBob.getExternal()).isNull();
	}

	@Test
	void testPushIgnoresPluginAccessFromSignerThatCannotReadRef() {
		user("+user/alice", "ROLE_USER");
		var bob = new User();
		bob.setTag("+user/bob");
		bob.setRole("ROLE_USER");
		bob.setTagReadAccess(new ArrayList<>(List.of("_plugin/secret")));
		userRepository.save(bob);
		var ref = new Ref();
		ref.setUrl("https://example.com/split");
		ref.setTags(new ArrayList<>(List.of("+user/alice", "_plugin/secret")));
		ref.setPlugin("_plugin/secret", Map.of("a", 1));
		refRepository.save(ref);

		replicator.push(remote("+user/alice", "+user/bob"));

		var pushedRef = pushedRef("https://example.com/split");
		assertThat(pushedRef.getTags()).containsExactly("+user/alice");
		assertThat(pushedRef.getPlugins()).isNull();
	}

	@Test
	void testPushSkipsUnreadableTagUrlAndContinues() {
		ref("tag:/_secret", "public");
		ref("https://example.com/later", "public");
		var remote = remote();
		remote.setPlugin("+plugin/origin/push", Map.of("batchSize", 1));

		replicator.push(remote);

		assertThat(pushed)
			.containsExactly("https://example.com/public", "https://example.com/later");
	}

	@Test
	void testPushIgnoresExternalFromSignerThatCannotReadUser() {
		var alice = new User();
		alice.setTag("+user/alice");
		alice.setRole("ROLE_USER");
		alice.setTagReadAccess(new ArrayList<>(List.of("_user/carol")));
		userRepository.save(alice);
		var bob = new User();
		bob.setTag("+user/bob");
		bob.setRole("ROLE_USER");
		bob.setTagWriteAccess(new ArrayList<>(List.of("_user/carol")));
		userRepository.save(bob);
		var carol = new User();
		carol.setTag("_user/carol");
		var external = new External();
		external.setIds(new ArrayList<>(List.of("carol@example.com")));
		carol.setExternal(external);
		userRepository.save(carol);

		replicator.push(remote("+user/alice", "+user/bob"));

		assertThat(pushedUser("_user/carol").getExternal()).isNull();
	}
}
