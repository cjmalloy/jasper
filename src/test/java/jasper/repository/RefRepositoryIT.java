package jasper.repository;

import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static jasper.config.JacksonConfiguration.om;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class RefRepositoryIT {

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ConfigCache configCache;

	@BeforeEach
	void init() {
		refRepository.deleteAllInBatch();
		pluginRepository.deleteAllInBatch();
		configCache.clearPluginCache();
	}

	// --- countPluginTagsInResponses ---

	@Test
	void testCountPluginTagsInResponses_ReturnsPluginTagsWithCounts() {
		var commentPlugin = new Plugin();
		commentPlugin.setTag("plugin/comment");
		commentPlugin.setOrigin("");
		pluginRepository.save(commentPlugin);

		var votePlugin = new Plugin();
		votePlugin.setTag("+plugin/vote/up");
		votePlugin.setOrigin("");
		pluginRepository.save(votePlugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setTags(List.of("public"));
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "+plugin/vote/up", "public"))
			.build());
		refRepository.save(response);

		var result = refRepository.countPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).hasSize(2);
		var map = result.stream().collect(java.util.stream.Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
		assertThat(map).containsEntry("plugin/comment", 1L);
		assertThat(map).containsEntry("+plugin/vote/up", 1L);
	}

	@Test
	void testCountPluginTagsInResponses_ExcludesSelf() {
		var selfPlugin = new Plugin();
		selfPlugin.setTag("plugin/comment");
		selfPlugin.setOrigin("");
		pluginRepository.save(selfPlugin);

		var self = new Ref();
		self.setUrl("http://example.com/self");
		self.setOrigin("");
		self.setSources(List.of("http://example.com/self"));
		self.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment"))
			.build());
		refRepository.save(self);

		var result = refRepository.countPluginTagsInResponses("http://example.com/self", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testCountPluginTagsInResponses_NoResponses() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var result = refRepository.countPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testCountPluginTagsInResponses_FiltersByOrigin() {
		var commentPlugin = new Plugin();
		commentPlugin.setTag("plugin/comment");
		commentPlugin.setOrigin("");
		pluginRepository.save(commentPlugin);

		var votePlugin = new Plugin();
		votePlugin.setTag("plugin/vote");
		votePlugin.setOrigin("");
		pluginRepository.save(votePlugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var resp1 = new Ref();
		resp1.setUrl("http://example.com/resp1");
		resp1.setOrigin("@test");
		resp1.setSources(List.of("http://example.com/parent"));
		resp1.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment"))
			.build());
		refRepository.save(resp1);

		var resp2 = new Ref();
		resp2.setUrl("http://example.com/resp2");
		resp2.setOrigin("@other");
		resp2.setSources(List.of("http://example.com/parent"));
		resp2.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/vote"))
			.build());
		refRepository.save(resp2);

		var result = refRepository.countPluginTagsInResponses("http://example.com/parent", "@test");

		assertThat(result).hasSize(1);
		assertThat((String) result.get(0)[0]).isEqualTo("plugin/comment");
		assertThat((Long) result.get(0)[1]).isEqualTo(1L);
	}

	// --- findAllUserPluginTagsInResponses ---

	@Test
	void testFindAllUserPluginTagsInResponses_ReturnsUserPluginTags() {
		var userTesterPlugin = new Plugin();
		userTesterPlugin.setTag("plugin/user/tester");
		userTesterPlugin.setOrigin("");
		pluginRepository.save(userTesterPlugin);

		var userAdminPlugin = new Plugin();
		userAdminPlugin.setTag("+plugin/user/admin");
		userAdminPlugin.setOrigin("");
		pluginRepository.save(userAdminPlugin);

		var commentPlugin = new Plugin();
		commentPlugin.setTag("plugin/comment");
		commentPlugin.setOrigin("");
		pluginRepository.save(commentPlugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/user/tester", "+plugin/user/admin", "plugin/comment"))
			.build());
		refRepository.save(response);

		var result = refRepository.findAllUserPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactlyInAnyOrder("plugin/user/tester", "+plugin/user/admin");
	}

	@Test
	void testFindAllUserPluginTagsInResponses_FiltersExactOrigin() {
		var userLocalPlugin = new Plugin();
		userLocalPlugin.setTag("plugin/user/local");
		userLocalPlugin.setOrigin("@test");
		pluginRepository.save(userLocalPlugin);

		var userRemotePlugin = new Plugin();
		userRemotePlugin.setTag("plugin/user/remote");
		userRemotePlugin.setOrigin("@other");
		pluginRepository.save(userRemotePlugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var resp1 = new Ref();
		resp1.setUrl("http://example.com/resp1");
		resp1.setOrigin("@test");
		resp1.setSources(List.of("http://example.com/parent"));
		resp1.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/user/local"))
			.build());
		refRepository.save(resp1);

		var resp2 = new Ref();
		resp2.setUrl("http://example.com/resp2");
		resp2.setOrigin("@other");
		resp2.setSources(List.of("http://example.com/parent"));
		resp2.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/user/remote"))
			.build());
		refRepository.save(resp2);

		var result = refRepository.findAllUserPluginTagsInResponses("http://example.com/parent", "@test");

		assertThat(result).containsExactly("plugin/user/local");
	}

	// --- originUrl ---

	@Test
	void testOriginUrl_FindsOriginRef() {
		var originRef = new Ref();
		originRef.setUrl("http://example.com/origin");
		originRef.setOrigin("@test");
		originRef.setTags(List.of("+plugin/origin"));
		originRef.setMetadata(Metadata.builder()
			.expandedTags(List.of("+plugin/origin"))
			.build());
		originRef.setPlugins(om().createObjectNode()
			.set("+plugin/origin", om().createObjectNode()));
		refRepository.save(originRef);

		var result = refRepository.originUrl("@test", "");

		assertThat(result).isPresent();
		assertThat(result.get().getUrl()).isEqualTo("http://example.com/origin");
	}

	@Test
	void testOriginUrl_WithProxy() {
		var originRef = new Ref();
		originRef.setUrl("http://example.com/origin");
		originRef.setOrigin("@remote");
		originRef.setTags(List.of("+plugin/origin"));
		originRef.setMetadata(Metadata.builder()
			.expandedTags(List.of("+plugin/origin"))
			.build());
		originRef.setPlugins(om().createObjectNode()
			.set("+plugin/origin", om().createObjectNode()
				.put("proxy", "http://proxy.example.com")));
		refRepository.save(originRef);

		var result = refRepository.originUrl("@remote", "");

		assertThat(result).isPresent();
		assertThat(result.get().getProxy()).isEqualTo("http://proxy.example.com");
		assertThat(result.get().get()).isEqualTo("http://proxy.example.com");
	}

	@Test
	void testOriginUrl_NotFound() {
		var result = refRepository.originUrl("@nonexistent", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testOriginUrl_FiltersByRemote() {
		var originRef = new Ref();
		originRef.setUrl("http://example.com/origin");
		originRef.setOrigin("@test");
		originRef.setTags(List.of("+plugin/origin"));
		originRef.setMetadata(Metadata.builder()
			.expandedTags(List.of("+plugin/origin"))
			.build());
		originRef.setPlugins(om().createObjectNode()
			.set("+plugin/origin", om().createObjectNode()
				.put("local", "@remote")));
		refRepository.save(originRef);

		var result1 = refRepository.originUrl("@test", "");
		assertThat(result1).isEmpty();

		var result2 = refRepository.originUrl("@test", "@remote");
		assertThat(result2).isPresent();
		assertThat(result2.get().getUrl()).isEqualTo("http://example.com/origin");
	}
}
