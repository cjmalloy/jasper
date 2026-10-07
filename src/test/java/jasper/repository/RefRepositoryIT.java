package jasper.repository;

import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.repository.filter.TagQuery;
import jasper.repository.spec.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jasper.config.JacksonConfiguration.om;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class RefRepositoryIT {

	@Autowired
	RefRepository refRepository;

	@Autowired
	RefRepositoryCustom refRepositoryCustom;

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

	// --- findAllPluginTagsInResponses ---

	@Test
	void testFindAllPluginTagsInResponses_ReturnsPluginTags() {
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

		var result = refRepositoryCustom.findAllPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactlyInAnyOrder("plugin/comment", "+plugin/vote/up");
		assertThat(result).doesNotContain("public");
	}

	@Test
	void testFindAllPluginTagsInResponses_ExcludesSelf() {
		var self = new Ref();
		self.setUrl("http://example.com/self");
		self.setOrigin("");
		self.setSources(List.of("http://example.com/self"));
		self.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment"))
			.build());
		refRepository.save(self);

		var result = refRepositoryCustom.findAllPluginTagsInResponses("http://example.com/self", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testFindAllPluginTagsInResponses_NoResponses() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var result = refRepositoryCustom.findAllPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testFindAllPluginTagsInResponses_FiltersByOrigin() {
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

		var result = refRepositoryCustom.findAllPluginTagsInResponses("http://example.com/parent", "@test");

		assertThat(result).containsExactly("plugin/comment");
	}

	@Test
	void testFindAllPluginTagsInResponses_EmptyPluginTable() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "public"))
			.build());
		refRepository.save(response);

		// Even with no plugins in the database, plugin tags in responses should still be found
		var result = refRepositoryCustom.findAllPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactly("plugin/comment");
		assertThat(result).doesNotContain("public");
	}

	// --- countPluginTagsInResponses ---

	@Test
	void testCountPluginTagsInResponses_ReturnsPluginTagsWithCounts() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var resp1 = new Ref();
		resp1.setUrl("http://example.com/resp1");
		resp1.setOrigin("");
		resp1.setSources(List.of("http://example.com/parent"));
		resp1.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "+plugin/vote/up", "public"))
			.build());
		refRepository.save(resp1);

		var resp2 = new Ref();
		resp2.setUrl("http://example.com/resp2");
		resp2.setOrigin("");
		resp2.setSources(List.of("http://example.com/parent"));
		resp2.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "public"))
			.build());
		refRepository.save(resp2);

		var result = refRepositoryCustom.countPluginTagsInResponses("http://example.com/parent", "");

		var map = result.stream().collect(java.util.stream.Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue()));
		assertThat(map).containsEntry("plugin/comment", 2L);
		assertThat(map).containsEntry("+plugin/vote/up", 1L);
		assertThat(map).doesNotContainKey("public");
	}

	@Test
	void testCountPluginTagsInResponses_EmptyPluginTable() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "public"))
			.build());
		refRepository.save(response);

		// Even with no plugins in the database, plugin tags in responses should still be counted
		var result = refRepositoryCustom.countPluginTagsInResponses("http://example.com/parent", "");

		var map = result.stream().collect(java.util.stream.Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue()));
		assertThat(map).containsEntry("plugin/comment", 1L);
		assertThat(map).doesNotContainKey("public");
	}

	@Test
	void testCountPluginTagsInResponses_CountsSameUrlInEachOrigin() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		for (var origin : List.of("", "@other")) {
			var response = new Ref();
			response.setUrl("http://example.com/response");
			response.setOrigin(origin);
			response.setSources(List.of("http://example.com/parent"));
			response.setMetadata(Metadata.builder()
				.expandedTags(List.of("plugin/comment", "public"))
				.build());
			refRepository.save(response);
		}

		var result = refRepositoryCustom.countPluginTagsInResponses("http://example.com/parent", "");

		var map = result.stream().collect(java.util.stream.Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue()));
		assertThat(map).containsEntry("plugin/comment", 2L);
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

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/parent", "");

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

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/parent", "@test");

		assertThat(result).containsExactly("plugin/user/local");
	}

	@Test
	void testFindAllUserPluginTagsInResponses_UnderscorePrefixMatchesLiteralOnly() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("_plugin/user/test", "+plugin/comment"))
			.build());
		refRepository.save(response);

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactly("_plugin/user/test");
		assertThat(result).doesNotContain("+plugin/comment");
	}

	@Test
	void testFindAllUserPluginTagsInResponses_WorksWithEmptyPluginTable() {
		// No plugins in the database at all
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/user/tester", "+plugin/user/admin", "plugin/comment", "public"))
			.build());
		refRepository.save(response);

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactlyInAnyOrder("plugin/user/tester", "+plugin/user/admin");
	}

	@Test
	void testFindAllUserPluginTagsInResponses_NoMatchingTags() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "public"))
			.build());
		refRepository.save(response);

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testFindAllUserPluginTagsInResponses_DeduplicatesAcrossResponses() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var resp1 = new Ref();
		resp1.setUrl("http://example.com/resp1");
		resp1.setOrigin("");
		resp1.setSources(List.of("http://example.com/parent"));
		resp1.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/user/tester", "public"))
			.build());
		refRepository.save(resp1);

		var resp2 = new Ref();
		resp2.setUrl("http://example.com/resp2");
		resp2.setOrigin("");
		resp2.setSources(List.of("http://example.com/parent"));
		resp2.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/user/tester", "+plugin/user/admin"))
			.build());
		refRepository.save(resp2);

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactlyInAnyOrder("plugin/user/tester", "+plugin/user/admin");
	}

	@Test
	void testFindAllUserPluginTagsInResponses_ExcludesSelf() {
		var self = new Ref();
		self.setUrl("http://example.com/self");
		self.setOrigin("");
		self.setSources(List.of("http://example.com/self"));
		self.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/user/tester"))
			.build());
		refRepository.save(self);

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/self", "");

		assertThat(result).isEmpty();
	}

	@Test
	void testFindAllUserPluginTagsInResponses_FallsBackToTagsWhenNoExpandedTags() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("plugin/user/tester", "public"));
		// No metadata / expandedTags set
		refRepository.save(response);

		var result = refRepositoryCustom.findAllUserPluginTagsInResponses("http://example.com/parent", "");

		assertThat(result).containsExactly("plugin/user/tester");
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

	@Test
	void testDropMetadata_MarksRefWithoutRegen() {
		var ref = new Ref();
		ref.setUrl("http://example.com/ref");
		ref.setOrigin("");
		ref.setMetadata(Metadata.builder().build());
		refRepository.save(ref);

		refRepository.dropMetadata("");

		var updated = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin()).orElseThrow();
		assertThat(updated.getMetadata().isRegen()).isTrue();
	}

	// --- markCascade ---

	@Test
	void testMarkCascade() {
		var ref = new Ref();
		ref.setUrl("http://example.com/response");
		ref.setOrigin("");
		ref.setMetadata(Metadata.builder()
			.modified("2026-01-01T00:00:00Z")
			.responses(List.of("http://example.com/other"))
			.obsolete(true)
			.build());
		refRepository.save(ref);
		assertThat(refRepository.getRefCascade("")).isEmpty();

		assertThat(refRepository.markCascade("http://example.com/response", "")).isEqualTo(1);

		var result = refRepository.findOneByUrlAndOrigin("http://example.com/response", "").orElseThrow();
		assertThat(result.getMetadata().isCascade()).isTrue();
		assertThat(result.getMetadata().getModified()).isEqualTo("2026-01-01T00:00:00Z");
		assertThat(result.getMetadata().getResponses()).containsExactly("http://example.com/other");
		assertThat(result.getMetadata().isObsolete()).isTrue();
		assertThat(refRepository.getRefCascade("")).get()
			.extracting(Ref::getUrl)
			.isEqualTo("http://example.com/response");
	}

	@Test
	void testMarkCascade_NullMetadata() {
		var ref = new Ref();
		ref.setUrl("http://example.com/response");
		ref.setOrigin("");
		refRepository.save(ref);

		assertThat(refRepository.markCascade("http://example.com/response", "")).isEqualTo(1);

		var result = refRepository.findOneByUrlAndOrigin("http://example.com/response", "").orElseThrow();
		assertThat(result.getMetadata().isCascade()).isTrue();
	}

	@Test
	void testMarkCascade_FiltersByUrlAndOrigin() {
		for (var origin : List.of("@test", "@test.sub")) {
			var ref = new Ref();
			ref.setUrl("http://example.com/response");
			ref.setOrigin(origin);
			ref.setMetadata(Metadata.builder().build());
			refRepository.save(ref);
		}

		assertThat(refRepository.markCascade("http://example.com/response", "@test")).isEqualTo(1);

		assertThat(refRepository.findOneByUrlAndOrigin("http://example.com/response", "@test").orElseThrow()
			.getMetadata().isCascade()).isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin("http://example.com/response", "@test.sub").orElseThrow()
			.getMetadata().isCascade()).isFalse();
	}

	// --- updateMetadata ---

	@Test
	void testUpdateMetadata_OnlyChangesMetadata() {
		var ref = new Ref();
		ref.setUrl("http://example.com/ref");
		ref.setTitle("Title");
		ref.setComment("Comment");
		ref.setTags(List.of("public", "+user/tester"));
		ref.setSources(List.of("http://example.com/source"));
		ref.setPlugins(om().createObjectNode()
			.set("+plugin/test", om().createObjectNode().put("value", 1)));
		ref.setPublished(Instant.parse("2025-01-01T00:00:00Z"));
		ref.setModified(Instant.parse("2025-02-01T00:00:00Z"));
		ref.setMetadata(Metadata.builder().modified("2025-02-01T00:00:00Z").build());
		refRepository.save(ref);
		var before = refRepository.findOneByUrlAndOrigin("http://example.com/ref", "").orElseThrow();

		assertThat(refRepository.updateMetadata("http://example.com/ref", "", Metadata.builder()
			.modified("2026-01-01T00:00:00Z")
			.responses(new ArrayList<>(List.of("http://example.com/response")))
			.cascade(true)
			.build())).isEqualTo(1);

		var after = refRepository.findOneByUrlAndOrigin("http://example.com/ref", "").orElseThrow();
		assertThat(after.getMetadata().getModified()).isEqualTo("2026-01-01T00:00:00Z");
		assertThat(after.getMetadata().getResponses()).containsExactly("http://example.com/response");
		assertThat(after.getMetadata().isCascade()).isTrue();
		assertThat(after.getTitle()).isEqualTo(before.getTitle());
		assertThat(after.getComment()).isEqualTo(before.getComment());
		assertThat(after.getTags()).isEqualTo(before.getTags());
		assertThat(after.getSources()).isEqualTo(before.getSources());
		assertThat(after.getPlugins()).isEqualTo(before.getPlugins());
		assertThat(after.getPublished()).isEqualTo(before.getPublished());
		assertThat(after.getModified()).isEqualTo(before.getModified());
	}

	@Test
	void testUpdateMetadata_FiltersByUrlAndOrigin() {
		for (var origin : List.of("@test", "@test.sub")) {
			var ref = new Ref();
			ref.setUrl("http://example.com/ref");
			ref.setOrigin(origin);
			ref.setMetadata(Metadata.builder().build());
			refRepository.save(ref);
		}

		assertThat(refRepository.updateMetadata("http://example.com/ref", "@test", Metadata.builder().cascade(true).build())).isEqualTo(1);

		assertThat(refRepository.findOneByUrlAndOrigin("http://example.com/ref", "@test").orElseThrow()
			.getMetadata().isCascade()).isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin("http://example.com/ref", "@test.sub").orElseThrow()
			.getMetadata().isCascade()).isFalse();
	}

	// --- findRemovedSources ---

	static final String CHILD = "http://example.com/child";

	Ref saveRef(String url, String origin, List<String> sources, Metadata metadata) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setOrigin(origin);
		ref.setSources(sources);
		ref.setMetadata(metadata);
		return refRepository.save(ref);
	}

	Ref saveSource(String url, String origin, String response) {
		return saveRef(url, origin, null, Metadata.builder()
			.responses(new ArrayList<>(List.of(response)))
			.build());
	}

	List<String> findRemovedSources(String url, String origin) {
		try (var stream = refRepository.findRemovedSources(url, origin)) {
			return stream.map(r -> r.getOrigin() + r.getUrl()).toList();
		}
	}

	@Test
	@Transactional
	void testFindRemovedSources_ReturnsUncitedSources() {
		saveRef(CHILD, "", List.of("http://example.com/a"), Metadata.builder().build());
		saveSource("http://example.com/a", "", CHILD);
		saveSource("http://example.com/b", "", CHILD);
		saveRef("http://example.com/c", "", null, Metadata.builder()
			.internalResponses(new ArrayList<>(List.of(CHILD)))
			.build());

		assertThat(findRemovedSources(CHILD, ""))
			.containsExactlyInAnyOrder("http://example.com/b", "http://example.com/c");
	}

	@Test
	@Transactional
	void testFindRemovedSources_ExcludesSelfAndObsolete() {
		saveRef(CHILD, "", List.of(), Metadata.builder()
			.responses(new ArrayList<>(List.of(CHILD)))
			.build());
		saveRef("http://example.com/obsolete", "", null, Metadata.builder()
			.responses(new ArrayList<>(List.of(CHILD)))
			.obsolete(true)
			.build());

		assertThat(findRemovedSources(CHILD, "")).isEmpty();
	}

	@Test
	@Transactional
	void testFindRemovedSources_DeletedRef() {
		saveSource("http://example.com/a", "", CHILD);
		saveSource("http://example.com/b", "", CHILD);

		assertThat(findRemovedSources(CHILD, ""))
			.containsExactlyInAnyOrder("http://example.com/a", "http://example.com/b");
	}

	@Test
	@Transactional
	void testFindRemovedSources_FiltersByOrigin() {
		saveSource("http://example.com/a", "@other", CHILD);
		saveSource("http://example.com/a", "@test.sub", CHILD);

		assertThat(findRemovedSources(CHILD, "@test"))
			.containsExactly("@test.subhttp://example.com/a");
	}

	@Test
	@Transactional
	void testFindRemovedSources_IgnoresObsoleteRef() {
		saveRef(CHILD, "", List.of("http://example.com/a"), Metadata.builder().obsolete(true).build());
		saveSource("http://example.com/a", "", CHILD);

		assertThat(findRemovedSources(CHILD, "")).containsExactly("http://example.com/a");
	}

	@Test
	@Transactional
	void testFindRemovedSourceUrls_ReturnsUncitedSourceUrls() {
		saveRef(CHILD, "", List.of("http://example.com/a"), Metadata.builder().build());
		saveSource("http://example.com/a", "", CHILD);
		saveSource("http://example.com/b", "", CHILD);
		saveSource("http://example.com/c", "@other", CHILD);

		assertThat(refRepository.findRemovedSourceUrls(CHILD, ""))
			.containsExactlyInAnyOrder("http://example.com/b", "http://example.com/c");
		assertThat(refRepository.findRemovedSourceUrls(CHILD, "@other"))
			.containsExactly("http://example.com/c");
	}

	// --- clearCascade / getRefCascade ---

	@Test
	void testClearCascade_MatchingModified() {
		var ref = saveRef(CHILD, "", null, Metadata.builder().cascade(true).build());

		assertThat(refRepository.clearCascade(CHILD, "", ref.getModified())).isEqualTo(1);

		assertThat(refRepository.findOneByUrlAndOrigin(CHILD, "").orElseThrow()
			.getMetadata().isCascade()).isFalse();
	}

	@Test
	void testClearCascade_MismatchedModified() {
		var ref = saveRef(CHILD, "", null, Metadata.builder().cascade(true).build());

		assertThat(refRepository.clearCascade(CHILD, "", ref.getModified().minusSeconds(1))).isEqualTo(0);

		assertThat(refRepository.findOneByUrlAndOrigin(CHILD, "").orElseThrow()
			.getMetadata().isCascade()).isTrue();
	}

	@Test
	void testGetRefCascade_FiltersByOrigin() {
		saveRef(CHILD, "@test.sub", null, Metadata.builder().cascade(true).build());

		assertThat(refRepository.getRefCascade("@test")).get()
			.extracting(Ref::getOrigin).isEqualTo("@test.sub");
		assertThat(refRepository.getRefCascade("@other")).isEmpty();
		assertThat(refRepository.getRefCascade("")).get()
			.extracting(Ref::getOrigin).isEqualTo("@test.sub");
	}

	@Test
	void testGetRefCascade_AllOrigins() {
		saveRef(CHILD, "@a", null, Metadata.builder().cascade(true).build());
		saveRef(CHILD, "@b", null, Metadata.builder().cascade(true).build());

		var first = refRepository.getRefCascade("").orElseThrow();
		refRepository.clearCascade(first.getUrl(), first.getOrigin(), first.getModified());
		var second = refRepository.getRefCascade("").orElseThrow();

		assertThat(List.of(first.getOrigin(), second.getOrigin())).containsExactlyInAnyOrder("@a", "@b");
	}

	@Test
	void testGetRefCascade_MostRecentlyModifiedFirst() {
		var older = new Ref();
		older.setUrl("http://example.com/older");
		older.setModified(Instant.now().minusSeconds(60));
		older.setMetadata(Metadata.builder().cascade(true).build());
		refRepository.save(older);
		saveRef("http://example.com/newer", "", null, Metadata.builder().cascade(true).build());

		assertThat(refRepository.getRefCascade("")).get()
			.extracting(Ref::getUrl).isEqualTo("http://example.com/newer");
	}

	// --- hasNoChildTag ---

	Ref saveTagged(String url, String... tags) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setOrigin("");
		ref.setTags(List.of(tags));
		return refRepository.save(ref);
	}

	@Test
	void testNoDescendents_ExcludesRefsWithChildTag() {
		saveTagged("http://example.com/parent", "people");
		saveTagged("http://example.com/child", "people", "people/murray");
		saveTagged("http://example.com/none", "science");

		assertThat(refRepository.findAll(RefSpec.hasNoChildTag("people")))
			.extracting(Ref::getUrl)
			.containsExactlyInAnyOrder("http://example.com/parent", "http://example.com/none");
	}

	@Test
	void testNoDescendents_KeepsParentTagItself() {
		saveTagged("http://example.com/parent", "people");

		assertThat(refRepository.findAll(RefSpec.hasNoChildTag("people")))
			.extracting(Ref::getUrl)
			.containsExactly("http://example.com/parent");
	}

	@Test
	void testNoDescendents_DoesNotMatchPrefixOfOtherTag() {
		// "peoples/x" is not a descendant of "people"
		saveTagged("http://example.com/other", "peoples/x");

		assertThat(refRepository.findAll(RefSpec.hasNoChildTag("people")))
			.extracting(Ref::getUrl)
			.containsExactly("http://example.com/other");
	}

	@Test
	void testNoDescendents_PrivateTagUnderscoreIsLiteral() {
		// Unescaped, "_secret/" would match "xsecret/" because _ is a LIKE wildcard
		saveTagged("http://example.com/wildcard", "xsecret/a");
		saveTagged("http://example.com/child", "_secret", "_secret/a");

		assertThat(refRepository.findAll(RefSpec.hasNoChildTag("_secret")))
			.extracting(Ref::getUrl)
			.containsExactly("http://example.com/wildcard");
	}

	@Test
	void testNoDescendents_NestedDescendant() {
		saveTagged("http://example.com/deep", "people/murray/anne");

		assertThat(refRepository.findAll(RefSpec.hasNoChildTag("people"))).isEmpty();
	}

	// --- tag fallback when expandedTags is missing ---

	Ref saveWithoutExpandedTags(String url, String... tags) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setOrigin("");
		ref.setTags(List.of(tags));
		ref.setMetadata(null); // no expandedTags → fallback to tags
		return refRepository.save(ref);
	}

	@Test
	void testNotTag_IncludesRefsWithoutExpandedTags() {
		saveWithoutExpandedTags("http://example.com/public", "public");
		saveWithoutExpandedTags("http://example.com/internal", "internal");

		assertThat(refRepository.findAll(new TagQuery("!internal").refSpec()))
			.extracting(Ref::getUrl)
			.containsExactly("http://example.com/public");
	}

	@Test
	void testTag_FallsBackToTagsWithoutExpandedTags() {
		saveWithoutExpandedTags("http://example.com/internal", "internal");

		assertThat(refRepository.findAll(new TagQuery("internal").refSpec()))
			.extracting(Ref::getUrl)
			.containsExactly("http://example.com/internal");
	}

	@Test
	void testNotTag_NullTagsAndNoExpandedTags() {
		var ref = new Ref();
		ref.setUrl("http://example.com/bare");
		ref.setOrigin("");
		refRepository.save(ref); // tags and metadata both null

		assertThat(refRepository.findAll(new TagQuery("!internal").refSpec()))
			.extracting(Ref::getUrl)
			.containsExactly("http://example.com/bare");
	}
}
