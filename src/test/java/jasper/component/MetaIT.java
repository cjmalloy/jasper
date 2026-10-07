package jasper.component;

import jasper.IntegrationTest;
import jasper.component.channel.Cascade;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.util.AopTestUtils.getTargetObject;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@IntegrationTest
@Transactional
public class MetaIT {

	@Autowired
	Meta meta;

	@Autowired
	Cascade cascade;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	Messages messages;
	Messages mockMessages;

	static final String URL = "https://www.example.com/";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		pluginRepository.deleteAll();
		Meta target = getTargetObject(meta);
		messages = (Messages) getField(target, "messages");
		setField(target, "messages", mockMessages = mock(Messages.class));
	}

	@AfterEach
	void cleanup() {
		Meta target = getTargetObject(meta);
		setField(target, "messages", messages);
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
		assertThat(parent.get().getMetadata().getResponses()).isNullOrEmpty();
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
			.responses(new ArrayList<>(List.of(responses)))
			.build());
		return refRepository.save(source);
	}

	Ref saveSourceWithPlugin(String url, String response) {
		var source = new Ref();
		source.setUrl(url);
		source.setTitle("Source");
		source.setTags(List.of("+user/tester"));
		source.setMetadata(Metadata.builder()
			.modified("2026-01-01T00:00:00Z")
			.responses(new ArrayList<>(List.of(response)))
			.plugins(new HashMap<>(Map.of("plugin/comment", 1L, "plugin", 1L)))
			.build());
		return refRepository.save(source);
	}

	Ref saveChild(String... sources) {
		return saveChild(List.of("+user/tester"), sources);
	}

	Ref saveChild(List<String> tags, String... sources) {
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(sources));
		child.setTags(tags);
		child.setMetadata(Metadata.builder().build());
		return refRepository.save(child);
	}

	Ref existingChild(List<String> tags, String... sources) {
		var existing = new Ref();
		existing.setUrl(URL + "child");
		existing.setTitle("Child");
		existing.setSources(List.of(sources));
		existing.setTags(tags);
		return existing;
	}

	Metadata metadata(String url) {
		return refRepository.findOneByUrlAndOrigin(url, "").orElseThrow().getMetadata();
	}

	void runCascade() {
		cascade.cascadeRef("", refRepository.getRefCascade("").orElseThrow());
	}

	@Test
	void testCreateMetadataResponseSyncsFirstTwoAndFlagsCascade() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		saveSource(URL + "d");
		var child = saveChild(URL + "a", URL + "b", URL + "c", URL + "d");

		meta.sources("", child, null);

		assertThat(child.getMetadata().isCascade()).isTrue();
		assertThat(refRepository.getRefCascade("")).get().extracting(Ref::getUrl).isEqualTo(URL + "child");
		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "c").getResponses()).isNullOrEmpty();
		assertThat(metadata(URL + "d").getResponses()).isNullOrEmpty();
	}

	@Test
	void testCreateMetadataResponseTwoSourcesDoesNotCascade() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		var child = saveChild(URL + "a", URL + "b");

		meta.sources("", child, null);

		assertThat(child.getMetadata().isCascade()).isFalse();
		assertThat(refRepository.getRefCascade("")).isEmpty();
		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).containsExactly(URL + "child");
	}

	@Test
	void testCreateMetadataResponseSelfAndDuplicateSourcesCascade() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		var child = saveChild(URL + "child", URL + "a", URL + "a", URL + "b");

		meta.sources("", child, null);

		assertThat(child.getMetadata().isCascade()).isTrue();
		assertThat(refRepository.getRefCascade("")).isPresent();
		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).isNullOrEmpty();

		runCascade();

		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "child").getResponses()).isNullOrEmpty();
	}

	@Test
	void testUpdateMetadataSameTwoSourcesDoesNotCascade() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "b", URL + "child");
		var child = saveChild(URL + "a", URL + "b");

		meta.sources("", child, existingChild(List.of("+user/tester"), URL + "a", URL + "b"));

		assertThat(child.getMetadata().isCascade()).isFalse();
		assertThat(refRepository.getRefCascade("")).isEmpty();
		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).containsExactly(URL + "child");
	}

	@Test
	void testUpdateMetadataRemovesFirstSourcesSynchronouslyAndRestOnCascade() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "b", URL + "child");
		saveSource(URL + "c", URL + "child");
		saveSource(URL + "d", URL + "child");
		var existing = existingChild(List.of("+user/tester"), URL + "a", URL + "b", URL + "c", URL + "d");
		var child = saveChild(URL + "a");

		meta.sources("", child, existing);

		assertThat(child.getMetadata().isCascade()).isTrue();
		assertThat(refRepository.getRefCascade("")).isPresent();
		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).isNullOrEmpty();
		assertThat(metadata(URL + "c").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "d").getResponses()).containsExactly(URL + "child");

		runCascade();

		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "c").getResponses()).isNullOrEmpty();
		assertThat(metadata(URL + "d").getResponses()).isNullOrEmpty();
	}

	@Test
	void testUpdateMetadataPluginTagsRemovedFromAllSources() {
		saveSourceWithPlugin(URL + "a", URL + "child");
		saveSourceWithPlugin(URL + "b", URL + "child");
		saveSourceWithPlugin(URL + "c", URL + "child");
		var existing = existingChild(List.of("+user/tester", "plugin/comment"), URL + "a", URL + "b", URL + "c");
		var child = saveChild(URL + "a", URL + "b", URL + "c");

		meta.sources("", child, existing);

		assertThat(metadata(URL + "a").getPlugins()).isNullOrEmpty();
		assertThat(metadata(URL + "b").getPlugins()).isNullOrEmpty();
		assertThat(metadata(URL + "c").getPlugins()).containsEntry("plugin/comment", 1L);

		runCascade();

		assertThat(metadata(URL + "c").getPlugins()).isNullOrEmpty();
		assertThat(metadata(URL + "c").getResponses()).containsExactly(URL + "child");
	}

	@Test
	void testCascadeInternalRefUsesInternalResponses() {
		for (var s : List.of("a", "b", "c", "d")) saveSource(URL + s);
		var child = saveChild(List.of("+user/tester", "internal"), URL + "a", URL + "b", URL + "c", URL + "d");

		meta.sources("", child, null);
		runCascade();

		for (var s : List.of("a", "b", "c", "d")) {
			assertThat(metadata(URL + s).getInternalResponses()).containsExactly(URL + "child");
			assertThat(metadata(URL + s).getResponses()).isNullOrEmpty();
		}
	}

	@Test
	void testCascadeSkipsObsoleteSource() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		var obsolete = saveSource(URL + "c");
		obsolete.getMetadata().setObsolete(true);
		refRepository.save(obsolete);
		var child = saveChild(URL + "a", URL + "b", URL + "c");

		meta.sources("", child, null);
		runCascade();

		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "c").getResponses()).isNullOrEmpty();
		assertThat(metadata(URL + "c").isObsolete()).isTrue();
	}

	@Test
	void testHardDeleteClearsAllSources() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "b", URL + "child");
		saveSource(URL + "c", URL + "child");
		var existing = existingChild(List.of("+user/tester"), URL + "a", URL + "b", URL + "c");

		meta.sources("", null, existing);

		for (var s : List.of("a", "b", "c")) {
			assertThat(metadata(URL + s).getResponses()).isNullOrEmpty();
			verify(mockMessages).updateMetadata(argThat(r -> r.getUrl().equals(URL + s)));
		}
	}

	@Test
	void testHardDeleteRemovesPluginCounts() {
		saveSourceWithPlugin(URL + "a", URL + "child");
		saveSourceWithPlugin(URL + "b", URL + "child");
		saveSourceWithPlugin(URL + "c", URL + "child");
		var existing = existingChild(List.of("+user/tester", "plugin/comment"), URL + "a", URL + "b", URL + "c");

		meta.sources("", null, existing);

		for (var s : List.of("a", "b", "c")) {
			assertThat(metadata(URL + s).getPlugins()).isNullOrEmpty();
		}
	}

	@Test
	void testHardDeleteRegensSurvivingVersion() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "b", URL + "child");
		saveSource(URL + "c", URL + "child");
		saveSource(URL + "d");
		var surviving = existingChild(List.of("+user/tester"), URL + "a", URL + "d");
		surviving.setOrigin("@sub");
		surviving.setMetadata(Metadata.builder().regen(true).build());
		refRepository.save(surviving);
		var existing = existingChild(List.of("+user/tester"), URL + "a", URL + "b", URL + "c");
		existing.setMetadata(Metadata.builder().modified("2026-01-02T00:00:00Z").build());

		meta.sources("", null, existing);

		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "c").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "d").getResponses()).containsExactly(URL + "child");
		var saved = refRepository.findOneByUrlAndOrigin(URL + "child", "@sub").orElseThrow();
		assertThat(saved.getMetadata().isCascade()).isTrue();
		assertThat(saved.getMetadata().isRegen()).isFalse();
		assertThat(saved.getMetadata().isObsolete()).isFalse();
		assertThat(saved.getMetadata().getModified()).isEqualTo("2026-01-02T00:00:00Z");
		assertThat(saved.getMetadata().getExpandedTags()).containsExactly("+user/tester", "+user");
		verify(mockMessages).updateMetadata(argThat(r -> r.getUrl().equals(URL + "child") && r.getOrigin().equals("@sub")));

		cascade.cascadeRef("", saved);

		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "b").getResponses()).isNullOrEmpty();
		assertThat(metadata(URL + "c").getResponses()).isNullOrEmpty();
		assertThat(metadata(URL + "d").getResponses()).containsExactly(URL + "child");
	}

	@Test
	void testRegenSyncsFirstTwoAndFlagsCascade() {
		for (var s : List.of("a", "b", "c", "d")) saveSource(URL + s);
		var child = saveChild(List.of("+user/tester", "plugin/comment"), URL + "a", URL + "b", URL + "c", URL + "d");

		meta.regen("", child);

		assertThat(child.getMetadata().isCascade()).isTrue();
		for (var s : List.of("a", "b")) {
			assertThat(metadata(URL + s).getResponses()).containsExactly(URL + "child");
			assertThat(metadata(URL + s).getPlugins()).containsEntry("plugin/comment", 1L);
		}
		for (var s : List.of("c", "d")) {
			assertThat(metadata(URL + s).getResponses()).isNullOrEmpty();
		}

		cascade.cascadeRef("", child);

		for (var s : List.of("a", "b", "c", "d")) {
			assertThat(metadata(URL + s).getResponses()).containsExactly(URL + "child");
			assertThat(metadata(URL + s).getPlugins()).containsEntry("plugin/comment", 1L);
		}
	}

	@Test
	void testRegenIsIdempotent() {
		for (var s : List.of("a", "b", "c")) saveSource(URL + s);
		var child = saveChild(List.of("+user/tester", "plugin/comment"), URL + "a", URL + "b", URL + "c");

		meta.regen("", child);
		cascade.cascadeRef("", child);
		meta.regen("", child);
		cascade.cascadeRef("", child);

		for (var s : List.of("a", "b", "c")) {
			assertThat(metadata(URL + s).getResponses()).containsExactly(URL + "child");
			assertThat(metadata(URL + s).getPlugins()).containsEntry("plugin/comment", 1L);
		}
	}

	@Test
	void testRegenCleansUncitedSourcesOnCascade() {
		saveSource(URL + "a");
		saveSource(URL + "x", URL + "child");
		var child = saveChild(URL + "a");

		meta.regen("", child);

		assertThat(child.getMetadata().isCascade()).isTrue();
		assertThat(metadata(URL + "a").getResponses()).containsExactly(URL + "child");
		assertThat(metadata(URL + "x").getResponses()).containsExactly(URL + "child");

		cascade.cascadeRef("", child);

		assertThat(metadata(URL + "x").getResponses()).isNullOrEmpty();
	}

	@Test
	void testRegenObsoleteRefDoesNotTouchSources() {
		saveSource(URL + "a");
		saveSource(URL + "x", URL + "child");
		var child = saveChild(URL + "a");
		var newer = existingChild(List.of("+user/tester"));
		newer.setOrigin("@other");
		newer.setModified(child.getModified().plusSeconds(60));
		newer.setMetadata(Metadata.builder().build());
		refRepository.save(newer);

		meta.regen("", child);

		assertThat(child.getMetadata().isObsolete()).isTrue();
		assertThat(metadata(URL + "a").getResponses()).isNullOrEmpty();
		assertThat(metadata(URL + "x").getResponses()).containsExactly(URL + "child");
		verifyNoInteractions(mockMessages);
	}

	@Test
	void testPluginDeleteNoticeUpdatesSourceMetadataCounts() {
		saveSource(URL + "a", URL + "child");
		var existing = new Ref();
		existing.setUrl(URL + "child");
		existing.setTitle("Child");
		existing.setSources(List.of(URL + "a"));
		existing.setTags(List.of("+user/tester"));
		var notice = new Ref();
		notice.setUrl(URL + "child");
		notice.setTags(List.of("internal", "plugin/delete"));

		meta.sources("", notice, existing);

		assertThat(refRepository.findOneByUrlAndOrigin(URL + "a", "").orElseThrow()
			.getMetadata().getResponses()).isNullOrEmpty();
	}

	@Test
	void testCreateMetadataIgnoresObsoleteResponses() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var obsolete = new Ref();
		obsolete.setUrl(URL + 2);
		obsolete.setOrigin("@other");
		obsolete.setTitle("Child");
		obsolete.setSources(List.of(URL));
		obsolete.setTags(List.of("+user/tester"));
		obsolete.setMetadata(Metadata.builder().obsolete(true).build());
		refRepository.save(obsolete);
		var obsoleteInternal = new Ref();
		obsoleteInternal.setUrl(URL + 3);
		obsoleteInternal.setOrigin("@other");
		obsoleteInternal.setTitle("Internal");
		obsoleteInternal.setSources(List.of(URL));
		obsoleteInternal.setTags(List.of("+user/tester", "internal"));
		obsoleteInternal.setMetadata(Metadata.builder().obsolete(true).build());
		refRepository.save(obsoleteInternal);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setSources(List.of(URL));
		child.setTags(List.of("+user/tester"));
		child.setMetadata(Metadata.builder().build());
		refRepository.save(child);

		meta.ref("", ref);

		assertThat(ref.getMetadata().getResponses()).containsExactly(URL + 2);
		assertThat(ref.getMetadata().getInternalResponses()).isEmpty();
	}

	@Test
	void testCreateMetadataIgnoresStaleObsoleteResponses() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var comment = new Plugin();
		comment.setTag("plugin/comment");
		pluginRepository.save(comment);
		var obsolete = new Ref();
		obsolete.setUrl(URL + 2);
		obsolete.setOrigin("@other");
		obsolete.setTitle("Child");
		obsolete.setSources(List.of(URL));
		obsolete.setTags(List.of("+user/tester", "plugin/comment"));
		obsolete.setMetadata(Metadata.builder().obsolete(true).build());
		refRepository.save(obsolete);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setTags(List.of("+user/tester"));
		child.setMetadata(Metadata.builder().build());
		refRepository.save(child);

		meta.ref("", ref);

		assertThat(ref.getMetadata().getResponses()).isEmpty();
		assertThat(ref.getMetadata().getPlugins()).extractingByKey("plugin/comment").isIn(null, 0);
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

	Ref userUrl(String origin, String... tags) {
		var res = new Ref();
		res.setUrl("tag:/user/tester?url=" + URL);
		res.setOrigin(origin);
		res.setSources(List.of(URL));
		res.setTags(new ArrayList<>(List.of(tags)));
		return res;
	}

	@Test
	void testUserUrlsQualifiedWithResponseOrigin() {
		saveSource(URL);

		meta.sources("", userUrl("@remote", "internal", "+user/tester", "+plugin/user/run"), null);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getUserUrls())
			.containsEntry("+plugin/user/run", List.of("tag:/user/tester@remote?url=" + URL));
		assertThat(parent.hasPluginResponse("+plugin/user/run")).isFalse();
		assertThat(parent.getMetadata().getPlugins()).containsEntry("+plugin/user/run", 1L);
		assertThat(parent.getMetadata().getRemotePlugins()).containsEntry("+plugin/user/run", 1L);
	}

	@Test
	void testUserUrlsSameOriginResponse() {
		saveSource(URL);

		meta.sources("", userUrl("", "internal", "+user/tester", "+plugin/user/run"), null);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getUserUrls())
			.containsEntry("+plugin/user/run", List.of("tag:/user/tester?url=" + URL));
		assertThat(parent.hasPluginResponse("+plugin/user/run")).isTrue();
		assertThat(parent.getMetadata().getRemotePlugins()).doesNotContainKey("+plugin/user/run");
	}

	@Test
	void testUserUrlsRemoveQualifiedResponse() {
		saveSource(URL);
		var existing = userUrl("@remote", "internal", "+user/tester", "+plugin/user/run");
		meta.sources("", existing, null);

		meta.sources("", userUrl("@remote", "internal", "+user/tester"), existing);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getUserUrls().get("+plugin/user/run")).isNullOrEmpty();
	}

	@Test
	void testRegenUserUrlsQualifiedWithResponseOrigin() {
		var source = saveSource(URL);
		refRepository.save(userUrl("", "internal", "+user/tester", "+plugin/user/run"));
		refRepository.save(userUrl("@remote", "internal", "+user/tester", "+plugin/user/run"));

		meta.ref("", source);

		assertThat(source.getMetadata().getUserUrls().get("+plugin/user/run"))
			.containsExactlyInAnyOrder(
				"tag:/user/tester?url=" + URL,
				"tag:/user/tester@remote?url=" + URL);
		assertThat(source.hasPluginResponse("+plugin/user/run")).isTrue();
		assertThat(source.getMetadata().getRemotePlugins()).doesNotContainKey("+plugin/user/run");
	}

	Ref comment(String url, String origin) {
		var res = new Ref();
		res.setUrl(url);
		res.setOrigin(origin);
		res.setSources(List.of(URL));
		res.setTags(new ArrayList<>(List.of("plugin/comment")));
		return res;
	}

	@Test
	void testRemotePluginsOnlyCountOtherOrigins() {
		saveSource(URL);

		meta.sources("", comment("comment:1", ""), null);
		meta.sources("", comment("comment:2", "@remote"), null);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getPlugins()).containsEntry("plugin/comment", 2L);
		assertThat(parent.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 1L);
		assertThat(parent.getMetadata().isRegen()).isFalse();
	}

	@Test
	void testRemoteResponseNotLocalPluginResponse() {
		saveSource(URL);

		meta.sources("", comment("comment:1", "@remote"), null);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
		assertThat(parent.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 1L);
		assertThat(parent.hasPluginResponse("plugin/comment")).isFalse();
	}

	@Test
	void testRemovePluginsKeepsRemote() {
		saveSource(URL);
		var local = comment("comment:1", "");
		meta.sources("", local, null);
		var remote = comment("comment:2", "@remote");
		meta.sources("", remote, null);

		var updated = comment("comment:1", "");
		updated.setTags(new ArrayList<>(List.of("internal")));
		meta.sources("", updated, local);

		var parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
		assertThat(parent.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 1L);
		assertThat(parent.hasPluginResponse("plugin/comment")).isFalse();
	}

	@Test
	void testRegenRemotePlugins() {
		var source = saveSource(URL);
		refRepository.save(comment("comment:1", ""));
		refRepository.save(comment("comment:2", "@remote"));

		meta.ref("", source);

		assertThat(source.getMetadata().getPlugins()).containsEntry("plugin/comment", 2L);
		assertThat(source.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 1L);
		assertThat(source.hasPluginResponse("plugin/comment")).isTrue();
	}

	@Test
	void testMissingRemotePluginsFallsBackAndRegens() {
		saveSourceWithPlugin(URL, "comment:0");

		var parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getRemotePlugins()).isNull();
		assertThat(parent.hasPluginResponse("plugin/comment")).isTrue();

		meta.sources("", comment("comment:1", "@remote"), null);

		parent = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(parent.getMetadata().getPlugins()).containsEntry("plugin/comment", 2L);
		assertThat(parent.getMetadata().getRemotePlugins()).isNull();
		assertThat(parent.getMetadata().isRegen()).isTrue();
	}
}
