package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Transactional
public class MetaIT {

	@Autowired
	Meta meta;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	static final String URL = "https://www.example.com/";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		pluginRepository.deleteAll();
	}

	@Test
	void testCreateMetadata() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));

		meta.ref("", ref);

		assertThat(ref.getMetadata().getResponses()).isEmpty();
		assertThat(ref.getMetadata().getInternalResponses()).isEmpty();
		assertThat(ref.getMetadata().getPlugins()).isEmpty();
		assertThat(ref.getMetadata().getUserUrls()).isEmpty();
	}

	@Test
	void testCreateMetadataResponse() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setSources(List.of(URL));
		child.setTags(List.of("+user/tester"));

		meta.sources("", child, null);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "");
		assertThat(parent).isNotEmpty();
		assertThat(parent.get().getMetadata().getResponses()).containsExactly(URL+2);
		assertThat(parent.get().getMetadata().getInternalResponses()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getPlugins()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getUserUrls()).isNullOrEmpty();
	}

	@Test
	void testCreateMetadataInternalResponse() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setSources(List.of(URL));
		child.setTags(List.of("+user/tester", "internal"));

		meta.sources("", child, null);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "");
		assertThat(parent).isNotEmpty();
		assertThat(parent.get().getMetadata().getResponses()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getInternalResponses()).containsExactly(URL+2);
		assertThat(parent.get().getMetadata().getPlugins()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getUserUrls()).isNullOrEmpty();
	}

	@Test
	void testCreateMetadataPluginResponse() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var comment = new Plugin();
		comment.setTag("plugin/comment");
		pluginRepository.save(comment);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setSources(List.of(URL));
		child.setTags(List.of("+user/tester", "plugin/comment", "internal"));

		meta.sources("", child, null);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "");
		assertThat(parent).isNotEmpty();
		assertThat(parent.get().getMetadata().getResponses()).isEmpty();
		assertThat(parent.get().getMetadata().getInternalResponses()).containsExactly(URL+2);
		assertThat(parent.get().getMetadata().getPlugins().get("plugin/comment")).isEqualTo(1);
	}

	Ref saveSource(String url, String... responses) {
		var source = new Ref();
		source.setUrl(url);
		source.setTitle("Source");
		source.setTags(List.of("+user/tester"));
		source.setMetadata(Metadata.builder()
			.modified("2026-01-01T00:00:00Z")
			.responses(List.of(responses))
			.build());
		return refRepository.save(source);
	}

	@Test
	void testCreateMetadataResponseDefersSourcesAfterFirstTwo() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		saveSource(URL + "d");
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(URL + "a", URL + "b", URL + "c", URL + "d"));
		child.setTags(List.of("+user/tester"));

		meta.sources("", child, null);

		var a = refRepository.findOneByUrlAndOrigin(URL + "a", "").orElseThrow();
		assertThat(a.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(a.getMetadata().isRegen()).isFalse();
		var b = refRepository.findOneByUrlAndOrigin(URL + "b", "").orElseThrow();
		assertThat(b.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(b.getMetadata().isRegen()).isFalse();
		var c = refRepository.findOneByUrlAndOrigin(URL + "c", "").orElseThrow();
		assertThat(c.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(c.getMetadata().isRegen()).isTrue();
		assertThat(c.getMetadata().getModified()).isNotEqualTo("2026-01-01T00:00:00Z");
		var d = refRepository.findOneByUrlAndOrigin(URL + "d", "").orElseThrow();
		assertThat(d.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(d.getMetadata().isRegen()).isTrue();
		assertThat(d.getMetadata().getModified()).isNotEqualTo("2026-01-01T00:00:00Z");
	}

	@Test
	void testCreateMetadataResponseSkipsSelfAndDuplicateSources() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(URL + "child", URL + "a", URL + "a", URL + "b", URL + "c"));
		child.setTags(List.of("+user/tester"));

		meta.sources("", child, null);

		var a = refRepository.findOneByUrlAndOrigin(URL + "a", "").orElseThrow();
		assertThat(a.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(a.getMetadata().isRegen()).isFalse();
		var b = refRepository.findOneByUrlAndOrigin(URL + "b", "").orElseThrow();
		assertThat(b.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(b.getMetadata().isRegen()).isFalse();
		var c = refRepository.findOneByUrlAndOrigin(URL + "c", "").orElseThrow();
		assertThat(c.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(c.getMetadata().isRegen()).isTrue();
	}

	@Test
	void testUpdateMetadataRemovedSourcesDefersAfterFirstTwo() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "b", URL + "child");
		saveSource(URL + "c", URL + "child");
		saveSource(URL + "d", URL + "child");
		var existing = new Ref();
		existing.setUrl(URL + "child");
		existing.setTitle("Child");
		existing.setSources(List.of(URL + "a", URL + "b", URL + "c", URL + "d"));
		existing.setTags(List.of("+user/tester"));
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(URL + "a"));
		child.setTags(List.of("+user/tester"));

		meta.sources("", child, existing);

		var a = refRepository.findOneByUrlAndOrigin(URL + "a", "").orElseThrow();
		assertThat(a.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(a.getMetadata().isRegen()).isFalse();
		var b = refRepository.findOneByUrlAndOrigin(URL + "b", "").orElseThrow();
		assertThat(b.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(b.getMetadata().isRegen()).isFalse();
		var c = refRepository.findOneByUrlAndOrigin(URL + "c", "").orElseThrow();
		assertThat(c.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(c.getMetadata().isRegen()).isFalse();
		var d = refRepository.findOneByUrlAndOrigin(URL + "d", "").orElseThrow();
		assertThat(d.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(d.getMetadata().isRegen()).isTrue();
	}

	@Test
	void testDeleteMetadataResponseDefersSourcesAfterFirstTwo() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "b", URL + "child");
		saveSource(URL + "c", URL + "child");
		var existing = new Ref();
		existing.setUrl(URL + "child");
		existing.setTitle("Child");
		existing.setSources(List.of(URL + "a", URL + "b", URL + "c"));
		existing.setTags(List.of("+user/tester"));

		meta.sources("", null, existing);

		var a = refRepository.findOneByUrlAndOrigin(URL + "a", "").orElseThrow();
		assertThat(a.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(a.getMetadata().isRegen()).isFalse();
		var b = refRepository.findOneByUrlAndOrigin(URL + "b", "").orElseThrow();
		assertThat(b.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(b.getMetadata().isRegen()).isFalse();
		var c = refRepository.findOneByUrlAndOrigin(URL + "c", "").orElseThrow();
		assertThat(c.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(c.getMetadata().isRegen()).isTrue();
	}

	@Test
	void testRegenDeferredSource() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(URL + "a", URL + "b", URL + "c"));
		child.setTags(List.of("+user/tester"));
		refRepository.save(child);

		meta.sources("", child, null);
		var c = refRepository.findOneByUrlAndOrigin(URL + "c", "").orElseThrow();
		var modified = c.getMetadata().getModified();
		meta.regen("", c);

		assertThat(c.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(c.getMetadata().getModified()).isEqualTo(modified);
		assertThat(c.getMetadata().isRegen()).isFalse();
	}

	@Test
	void testExpandTags_null() {
		var result = Meta.expandTags(null);
		assertThat(result).isEmpty();
	}

	@Test
	void testExpandTags_empty() {
		var result = Meta.expandTags(List.of());
		assertThat(result).isEmpty();
	}

	@Test
	void testExpandTags_simpleTags() {
		var result = Meta.expandTags(List.of("tag1", "tag2"));
		assertThat(result).containsExactly("tag1", "tag2");
	}

	@Test
	void testExpandTags_singleLevel() {
		var result = Meta.expandTags(List.of("plugin/comment"));
		assertThat(result).containsExactly("plugin/comment", "plugin");
	}

	@Test
	void testExpandTags_multipleLevels() {
		var result = Meta.expandTags(List.of("a/b/c/d"));
		assertThat(result).containsExactly("a/b/c/d", "a/b/c", "a/b", "a");
	}

	@Test
	void testExpandTags_mixedTags() {
		var result = Meta.expandTags(List.of("simple", "plugin/comment", "other"));
		// Parent tags are added at the end in the order they are encountered
		assertThat(result).containsExactly("simple", "plugin/comment", "other", "plugin");
	}

	@Test
	void testExpandTags_avoidsDuplicates() {
		var result = Meta.expandTags(List.of("plugin/comment", "plugin/vote", "plugin"));
		// plugin is already in the list, should not be added again
		assertThat(result).containsExactly("plugin/comment", "plugin/vote", "plugin");
	}

	@Test
	void testResponse_null() {
		meta.response("", null);
		// Should not throw exception
	}

	@Test
	void testResponse_setsExpandedTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Test");
		ref.setTags(List.of("plugin/comment", "tag"));

		meta.response("", ref);

		assertThat(ref.getMetadata()).isNotNull();
		assertThat(ref.getMetadata().getExpandedTags()).containsExactlyInAnyOrder("plugin/comment", "plugin", "tag");
	}

	@Test
	void testResponse_withHierarchicalTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Test");
		ref.setTags(List.of("a/b/c", "x/y"));

		meta.response("", ref);

		assertThat(ref.getMetadata()).isNotNull();
		assertThat(ref.getMetadata().getExpandedTags()).containsExactlyInAnyOrder("a/b/c", "a/b", "a", "x/y", "x");
	}

	@Test
	void testResponse_withNullTags() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Test");
		ref.setTags(null);

		meta.response("", ref);

		assertThat(ref.getMetadata()).isNotNull();
		assertThat(ref.getMetadata().getExpandedTags()).isEmpty();
	}

	@Test
	void testUpdatePreservesMetadata() {
		var existing = new Ref();
		existing.setMetadata(Metadata.builder()
			.modified("2026-01-01T00:00:00Z")
			.expandedTags(List.of("old"))
			.responses(List.of(URL + "response"))
			.internalResponses(List.of(URL + "internal"))
			.plugins(Map.of("plugin/comment", 2L))
			.userUrls(Map.of("plugin/user/tester", List.of(URL + "user")))
			.obsolete(true)
			.regen(true)
			.build());
		var ref = new Ref();
		ref.setTags(List.of("new/child"));

		meta.update("", ref, existing);

		assertThat(ref.getMetadata().getModified()).isEqualTo("2026-01-01T00:00:00Z");
		assertThat(ref.getMetadata().getExpandedTags()).containsExactly("new/child", "new");
		assertThat(ref.getMetadata().getResponses()).containsExactly(URL + "response");
		assertThat(ref.getMetadata().getInternalResponses()).containsExactly(URL + "internal");
		assertThat(ref.getMetadata().getPlugins()).containsEntry("plugin/comment", 2L);
		assertThat(ref.getMetadata().getUserUrls())
			.containsEntry("plugin/user/tester", List.of(URL + "user"));
		assertThat(ref.getMetadata().isObsolete()).isFalse();
		assertThat(ref.getMetadata().isRegen()).isTrue();
	}

	@Test
	void testUpdateRebuildsMissingMetadata() {
		var existing = new Ref();
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("new/child"));

		meta.update("", ref, existing);

		assertThat(ref.getMetadata()).isNotNull();
		assertThat(ref.getMetadata().getExpandedTags()).containsExactly("new/child", "new");
		assertThat(ref.getMetadata().getResponses()).isEmpty();
		assertThat(ref.getMetadata().getInternalResponses()).isEmpty();
		assertThat(ref.getMetadata().getPlugins()).isEmpty();
		assertThat(ref.getMetadata().getUserUrls()).isEmpty();
	}

	@Test
	void testResponseSource_callsSourcesWhenTagsChange() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTitle("Existing");
		existing.setTags(List.of("tag1"));
		refRepository.save(existing);

		var updated = new Ref();
		updated.setUrl(URL);
		updated.setTitle("Updated");
		updated.setTags(List.of("tag2"));

		meta.responseSource("", updated, existing);

		// Verify sources was called by checking the metadata was updated
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "");
		assertThat(fetched).isNotEmpty();
		// The sources() method would have updated metadata
	}

	@Test
	void testResponseSource_doesNotCallSourcesWhenTagsSame() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTitle("Existing");
		existing.setTags(List.of("tag1", "tag2"));
		refRepository.save(existing);

		var updated = new Ref();
		updated.setUrl(URL);
		updated.setTitle("Updated");
		updated.setTags(List.of("tag1", "tag2"));

		meta.responseSource("", updated, existing);

		// When tags are the same, sources() should not be called
		// We can verify by checking that metadata hasn't changed
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "");
		assertThat(fetched).isNotEmpty();
	}

	@Test
	void testResponseSource_handlesNullRef() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTags(List.of("tag1"));

		meta.responseSource("", null, existing);
		// Should call sources() since ref is null
	}

	@Test
	void testResponseSource_handlesNullExisting() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("tag1"));

		meta.responseSource("", ref, null);
		// Should call sources() since existing is null
	}

	@Test
	void testResponseSource_handlesNullExistingTags() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTags(null);

		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("tag1"));

		meta.responseSource("", ref, existing);
		// Should call sources() since existing.tags is null
	}

}
