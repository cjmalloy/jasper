package jasper.repository;

import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static jasper.config.JacksonConfiguration.om;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class RefRepositoryCustomIT {

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ConfigCache configCache;

	@PersistenceContext
	EntityManager em;

	@BeforeEach
	void init() {
		refRepository.deleteAllInBatch();
		pluginRepository.deleteAllInBatch();
		configCache.clearUserCache();
		configCache.clearPluginCache();
		configCache.clearTemplateCache();
	}

	// --- findAllPluginTagsInResponses ---

	@Test
	void testFindAllPluginTagsInResponses_ReturnsPluginTags() {
		// Parent ref
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setTags(List.of("public"));
		refRepository.save(parent);

		// Response ref with plugin tags in metadata.expandedTags and parent in sources
		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "+plugin/vote/up", "public"))
			.build());
		refRepository.save(response);

		var result = refRepository.findAllPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactlyInAnyOrder("plugin/comment", "+plugin/vote/up");
	}

	@Test
	void testFindAllPluginTagsInResponses_ExcludesSelf() {
		// A ref that references itself in sources
		var self = new Ref();
		self.setUrl("http://example.com/self");
		self.setOrigin("");
		self.setSources(List.of("http://example.com/self"));
		self.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment"))
			.build());
		refRepository.save(self);

		var result = refRepository.findAllPluginTagsInResponses("http://example.com/self", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testFindAllPluginTagsInResponses_NoResponses() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var result = refRepository.findAllPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testFindAllPluginTagsInResponses_FiltersByOrigin() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		// Response from matching origin
		var resp1 = new Ref();
		resp1.setUrl("http://example.com/resp1");
		resp1.setOrigin("@test");
		resp1.setSources(List.of("http://example.com/parent"));
		resp1.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment"))
			.build());
		refRepository.save(resp1);

		// Response from non-matching origin
		var resp2 = new Ref();
		resp2.setUrl("http://example.com/resp2");
		resp2.setOrigin("@other");
		resp2.setSources(List.of("http://example.com/parent"));
		resp2.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/vote"))
			.build());
		refRepository.save(resp2);

		var result = refRepository.findAllPluginTagsInResponses("http://example.com/parent", "@test");

		assertThat(result).containsExactly("plugin/comment");
	}

	// --- findAllUserPluginTagsInResponses ---

	@Test
	void testFindAllUserPluginTagsInResponses_ReturnsUserPluginTags() {
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

		// Should only include user plugin tags, not plugin/comment
		assertThat(result).containsExactlyInAnyOrder("plugin/user/tester", "+plugin/user/admin");
	}

	@Test
	void testFindAllUserPluginTagsInResponses_FiltersExactOrigin() {
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

		// Should not find when remote doesn't match
		var result1 = refRepository.originUrl("@test", "");
		assertThat(result1).isEmpty();

		// Should find when remote matches
		var result2 = refRepository.originUrl("@test", "@remote");
		assertThat(result2).isPresent();
		assertThat(result2.get().getUrl()).isEqualTo("http://example.com/origin");
	}

	// --- backfillMetadata ---

	@Test
	void testBackfillMetadata_BackfillsNullMetadata() {
		// Create plugin for the aggregation
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		// Parent ref with null metadata (needs backfill)
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setTags(List.of("public"));
		parent.setMetadata(null);
		refRepository.save(parent);

		// Response ref
		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("public"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("public"))
			.build());
		refRepository.save(response);

		int updated = refRepository.backfillMetadata("", 10);

		assertThat(updated).isGreaterThanOrEqualTo(1);

		// Verify metadata was populated using native query (avoids Hibernate deserialization)
		var metadataJson = (String) em.createNativeQuery(
			"SELECT metadata FROM ref WHERE url = :url AND origin = :origin")
			.setParameter("url", parent.getUrl())
			.setParameter("origin", parent.getOrigin())
			.getSingleResult();
		assertThat(metadataJson).isNotNull();
		assertThat(metadataJson).contains("\"modified\"");
		assertThat(metadataJson).contains("\"obsolete\"");
	}

	@Test
	void testBackfillMetadata_BackfillsRegenFlag() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(Metadata.builder().regen(true).build());
		refRepository.save(parent);

		int updated = refRepository.backfillMetadata("", 10);

		assertThat(updated).isEqualTo(1);
	}

	@Test
	void testBackfillMetadata_RespectsOriginFilter() {
		var ref1 = new Ref();
		ref1.setUrl("http://example.com/ref1");
		ref1.setOrigin("@test");
		ref1.setMetadata(null);
		refRepository.save(ref1);

		var ref2 = new Ref();
		ref2.setUrl("http://example.com/ref2");
		ref2.setOrigin("@other");
		ref2.setMetadata(null);
		refRepository.save(ref2);

		int updated = refRepository.backfillMetadata("@test", 10);

		assertThat(updated).isEqualTo(1);
	}

	@Test
	void testBackfillMetadata_ReturnsZeroWhenNothingToBackfill() {
		var ref = new Ref();
		ref.setUrl("http://example.com/ref");
		ref.setOrigin("");
		ref.setMetadata(Metadata.builder().build());
		refRepository.save(ref);

		int updated = refRepository.backfillMetadata("", 10);

		assertThat(updated).isEqualTo(0);
	}

	// --- GIN index management ---

	@Test
	void testBuildAndDropTags() {
		refRepository.dropTags();
		refRepository.buildTags();
		refRepository.dropTags();
		// No exception means success
	}

	@Test
	void testBuildAndDropSources() {
		refRepository.dropSources();
		refRepository.buildSources();
		refRepository.dropSources();
	}

	@Test
	void testBuildAndDropPublished() {
		refRepository.dropPublished();
		refRepository.buildPublished();
		refRepository.dropPublished();
	}

	@Test
	void testBuildAndDropModified() {
		refRepository.dropModified();
		refRepository.buildModified();
		refRepository.dropModified();
	}
}
