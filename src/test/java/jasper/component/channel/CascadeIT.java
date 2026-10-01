package jasper.component.channel;

import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.component.Messages;
import jasper.component.Meta;
import jasper.config.Config.ServerConfig;
import jasper.config.Props;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import jasper.service.dto.MetadataDto;
import jasper.service.dto.RefDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.AopTestUtils.getTargetObject;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@IntegrationTest
public class CascadeIT {

	@Autowired
	Cascade cascade;

	@Autowired
	Meta meta;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Props props;

	Messages messages;
	Messages mockMessages;
	ConfigCache configs;
	int cascadeBatchSize;

	static final String URL = "https://www.example.com/";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		Meta target = getTargetObject(meta);
		messages = (Messages) getField(target, "messages");
		setField(target, "messages", mockMessages = mock(Messages.class));
		Cascade cascadeTarget = getTargetObject(cascade);
		configs = (ConfigCache) getField(cascadeTarget, "configs");
		cascadeBatchSize = props.getCascadeBatchSize();
		cascade.dirty = false;
	}

	@AfterEach
	void cleanup() {
		Meta target = getTargetObject(meta);
		setField(target, "messages", messages);
		Cascade cascadeTarget = getTargetObject(cascade);
		setField(cascadeTarget, "configs", configs);
		setField(cascadeTarget, "meta", meta);
		props.setCascadeBatchSize(cascadeBatchSize);
		refRepository.deleteAll();
	}

	void setScriptSelectors(String... selectors) {
		var mockConfigs = mock(ConfigCache.class);
		when(mockConfigs.root()).thenReturn(ServerConfig.builder().scriptSelectors(List.of(selectors)).build());
		Cascade target = getTargetObject(cascade);
		setField(target, "configs", mockConfigs);
	}

	Ref saveFlagged(String url) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setTitle("Flagged");
		ref.setTags(List.of("+user/tester"));
		ref.setMetadata(Metadata.builder().cascade(true).build());
		return refRepository.save(ref);
	}

	long countFlagged() {
		return refRepository.findAll().stream()
			.filter(r -> r.getMetadata() != null && r.getMetadata().isCascade())
			.count();
	}

	static RefDto refDto(MetadataDto metadata) {
		var ref = new RefDto();
		ref.setUrl(URL);
		ref.setMetadata(metadata);
		return ref;
	}

	Ref saveSource(String url) {
		var source = new Ref();
		source.setUrl(url);
		source.setTitle("Source");
		source.setTags(List.of("+user/tester"));
		source.setMetadata(Metadata.builder()
			.modified("2026-01-01T00:00:00Z")
			.build());
		return refRepository.save(source);
	}

	Ref saveChild(String... sources) {
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(sources));
		child.setTags(List.of("+user/tester", "plugin/comment"));
		child.setMetadata(Metadata.builder().build());
		return refRepository.save(child);
	}

	@Test
	void testRemainingSourcesAreUpdatedOnCascade() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		saveSource(URL + "d");
		var child = saveChild(URL + "a", URL + "b", URL + "c", URL + "d");
		meta.sources("", child, null);
		assertThat(refRepository.getRefCascade("")).isPresent();
		for (var url : List.of(URL + "c", URL + "d")) {
			assertThat(refRepository.findOneByUrlAndOrigin(url, "").orElseThrow()
				.getMetadata().getResponses()).isNullOrEmpty();
		}

		cascade.cascadeOrigin("");

		for (var url : List.of(URL + "a", URL + "b", URL + "c", URL + "d")) {
			var source = refRepository.findOneByUrlAndOrigin(url, "").orElseThrow();
			assertThat(source.getMetadata().getResponses()).containsExactly(URL + "child");
			assertThat(source.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
		}
		assertThat(refRepository.findOneByUrlAndOrigin(URL + "child", "").orElseThrow()
			.getMetadata().isCascade()).isFalse();
		assertThat(refRepository.getRefCascade("")).isEmpty();
	}

	@Test
	void testCascadeRemovesUncitedSources() {
		for (var url : List.of(URL + "a", URL + "b", URL + "c", URL + "d")) {
			var source = saveSource(url);
			source.getMetadata().setResponses(List.of(URL + "child"));
			refRepository.save(source);
		}
		var child = saveChild(URL + "a");
		child.getMetadata().setCascade(true);
		refRepository.save(child);

		cascade.cascadeOrigin("");

		for (var url : List.of(URL + "b", URL + "c", URL + "d")) {
			assertThat(refRepository.findOneByUrlAndOrigin(url, "").orElseThrow()
				.getMetadata().getResponses()).isNullOrEmpty();
			verify(mockMessages, atLeastOnce()).updateMetadata(argThat(r -> r.getUrl().equals(url)));
		}
		assertThat(refRepository.findOneByUrlAndOrigin(URL + "a", "").orElseThrow()
			.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(refRepository.getRefCascade("")).isEmpty();
	}

	@Test
	void testCascadeRetriesWhenRefModified() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		var child = saveChild(URL + "a", URL + "b", URL + "c");
		meta.sources("", child, null);
		child.getMetadata().setCascade(true);
		refRepository.save(child);
		var stale = refRepository.findOneByUrlAndOrigin(URL + "child", "").orElseThrow();

		assertThat(refRepository.clearCascade(URL + "child", "", stale.getModified().minusSeconds(1))).isEqualTo(0);
		assertThat(refRepository.getRefCascade("")).isPresent();
		assertThat(refRepository.clearCascade(URL + "child", "", stale.getModified())).isEqualTo(1);
		assertThat(refRepository.getRefCascade("")).isEmpty();
	}

	@Test
	void testRemainingSourcesAreUpdatedInBatches() {
		var sourceUrls = IntStream.range(0, 1003)
			.mapToObj(i -> URL + "source/" + i)
			.toList();
		var modified = Instant.now();
		refRepository.saveAll(List.of(0, 1, 2, 999, 1000, 1002).stream().map(i -> {
			var source = new Ref();
			source.setUrl(sourceUrls.get(i));
			source.setTitle("Source");
			source.setTags(List.of("+user/tester"));
			source.setModified(modified.minusMillis(i));
			return source;
		}).toList());
		var child = saveChild(sourceUrls.toArray(String[]::new));

		meta.sources("", child, null);
		cascade.cascadeOrigin("");

		for (var i : List.of(2, 999, 1000, 1002)) {
			assertThat(refRepository.findOneByUrlAndOrigin(sourceUrls.get(i), "").orElseThrow()
				.getMetadata().getResponses()).containsExactly(URL + "child");
		}
		assertThat(refRepository.getRefCascade("")).isEmpty();
	}

	@Test
	void testHandleRefUpdateWithoutMetadata() {
		assertThatCode(() -> cascade.handleRefUpdate(MessageBuilder.withPayload(refDto(null)).build()))
			.doesNotThrowAnyException();

		assertThat(cascade.dirty).isFalse();
	}

	@Test
	void testHandleRefUpdateWithoutCascade() {
		cascade.handleRefUpdate(MessageBuilder.withPayload(refDto(new MetadataDto())).build());

		assertThat(cascade.dirty).isFalse();
	}

	@Test
	void testHandleRefUpdateWithCascade() {
		var metadata = new MetadataDto();
		metadata.setCascade(true);

		cascade.handleRefUpdate(MessageBuilder.withPayload(refDto(metadata)).build());

		assertThat(cascade.dirty).isTrue();
	}

	@Test
	void testCascadeResetsDirty() {
		cascade.dirty = true;

		cascade.cascade();

		assertThat(cascade.dirty).isFalse();
	}

	@Test
	void testCascadeBatchLimit() {
		saveFlagged(URL + "1");
		saveFlagged(URL + "2");
		saveFlagged(URL + "3");
		props.setCascadeBatchSize(2);

		cascade.cascadeOrigin("");

		assertThat(countFlagged()).isEqualTo(1);
	}

	@Test
	void testCascadeFailureClearsFlag() {
		saveFlagged(URL + "fail");
		saveFlagged(URL + "ok");
		var mockMeta = mock(Meta.class);
		doAnswer(invocation -> {
			Ref ref = invocation.getArgument(1);
			if (ref.getUrl().equals(URL + "fail")) throw new RuntimeException("Test failure");
			meta.cascade(invocation.getArgument(0), ref);
			return null;
		}).when(mockMeta).cascade(any(), any());
		Cascade target = getTargetObject(cascade);
		setField(target, "meta", mockMeta);

		assertThatCode(() -> cascade.cascadeOrigin("")).doesNotThrowAnyException();

		verify(mockMeta).cascade(argThat(""::equals), argThat(r -> r.getUrl().equals(URL + "fail")));
		verify(mockMeta).cascade(argThat(""::equals), argThat(r -> r.getUrl().equals(URL + "ok")));
		assertThat(countFlagged()).isZero();
	}

	@Test
	void testCascadeRefModifiedDuringCascadeStaysFlagged() {
		saveFlagged(URL + "child");
		var mockMeta = mock(Meta.class);
		doAnswer(invocation -> {
			var ref = refRepository.findOneByUrlAndOrigin(URL + "child", "").orElseThrow();
			ref.setModified(ref.getModified().plusSeconds(60));
			refRepository.save(ref);
			return null;
		}).when(mockMeta).cascade(any(), any());
		Cascade target = getTargetObject(cascade);
		setField(target, "meta", mockMeta);
		props.setCascadeBatchSize(1);

		cascade.cascadeOrigin("");

		assertThat(refRepository.getRefCascade("")).isPresent();
	}

	@Test
	void testCascadeDisabledForOrigin() {
		saveFlagged(URL + "child");
		setScriptSelectors("+plugin/other");

		cascade.cascadeOrigin("");

		assertThat(refRepository.getRefCascade("")).isPresent();
	}
}
