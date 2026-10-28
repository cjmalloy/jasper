package jasper.component;

import jasper.DisabledOnSqlite;
import jasper.IntegrationTest;
import jasper.component.channel.Cascade;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.util.AopTestUtils.getTargetObject;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

/**
 * Concurrent edits are real committed writes, so this class must not be @Transactional.
 */
@IntegrationTest
public class MetaConcurrencyIT {

	@Autowired
	Meta meta;

	@Autowired
	Cascade cascade;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	Messages messages;
	Messages mockMessages;

	static final String URL = "https://www.example.com/";
	static final Instant EDITED = Instant.parse("2030-01-01T00:00:00Z");

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		Meta target = getTargetObject(meta);
		messages = (Messages) getField(target, "messages");
		setField(target, "messages", mockMessages = mock(Messages.class));
	}

	@AfterEach
	void cleanup() {
		Meta target = getTargetObject(meta);
		setField(target, "messages", messages);
		refRepository.deleteAll();
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

	Ref saveChild(String... sources) {
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(sources));
		child.setTags(List.of("+user/tester", "plugin/comment"));
		child.setMetadata(Metadata.builder().build());
		return refRepository.save(child);
	}

	Ref existingChild(String... sources) {
		var existing = new Ref();
		existing.setUrl(URL + "child");
		existing.setTitle("Child");
		existing.setSources(List.of(sources));
		existing.setTags(List.of("+user/tester", "plugin/comment"));
		return existing;
	}

	Ref find(String url, String origin) {
		return refRepository.findOneByUrlAndOrigin(url, origin).orElseThrow();
	}

	void edit(String url, String origin) {
		var tx = new TransactionTemplate(transactionManager);
		tx.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
		tx.executeWithoutResult(status -> {
			var ref = find(url, origin);
			ref.setTitle("Edited");
			ref.setTags(List.of("+user/tester", "edited"));
			ref.setModified(EDITED);
			refRepository.save(ref);
		});
	}

	/**
	 * Commit an edit from another connection, failing instead of waiting on a held row lock.
	 */
	void editConcurrently(String url, String origin) throws Exception {
		CompletableFuture.runAsync(() -> edit(url, origin)).get(5, SECONDS);
	}

	void assertEdited(String url, String origin) {
		var ref = find(url, origin);
		assertThat(ref.getTitle()).isEqualTo("Edited");
		assertThat(ref.getTags()).containsExactly("+user/tester", "edited");
		assertThat(ref.getModified()).isEqualTo(EDITED);
	}

	/**
	 * When the first of the given sources is sent as a metadata update, commit an edit
	 * to the other source, which has already been loaded.
	 */
	AtomicReference<String> editOtherOnFirstUpdate(String first, String second, AtomicReference<Throwable> failure) {
		var edited = new AtomicReference<String>();
		doAnswer(invocation -> {
			Ref ref = invocation.getArgument(0);
			if (edited.get() == null && (ref.getUrl().equals(first) || ref.getUrl().equals(second))) {
				edited.set(ref.getUrl().equals(first) ? second : first);
				try {
					editConcurrently(edited.get(), "");
				} catch (Exception e) {
					failure.set(e);
				}
			}
			return null;
		}).when(mockMessages).updateMetadata(any());
		return edited;
	}

	@Test
	void testSourcesKeepsConcurrentContentEdits() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		var child = saveChild(URL + "a", URL + "b");
		var failure = new AtomicReference<Throwable>();
		var edited = editOtherOnFirstUpdate(URL + "a", URL + "b", failure);

		meta.sources("", child, null);

		assertThat(failure.get()).isNull();
		assertThat(edited.get()).isNotNull();
		assertEdited(edited.get(), "");
		assertThat(find(edited.get(), "").getMetadata().getResponses()).containsExactly(URL + "child");
	}

	@Test
	void testRemoveSourceKeepsConcurrentContentEdits() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "b", URL + "child");
		var child = saveChild();
		var failure = new AtomicReference<Throwable>();
		var edited = editOtherOnFirstUpdate(URL + "a", URL + "b", failure);

		meta.sources("", child, existingChild(URL + "a", URL + "b"));

		assertThat(failure.get()).isNull();
		assertThat(edited.get()).isNotNull();
		assertEdited(edited.get(), "");
		assertThat(find(edited.get(), "").getMetadata().getResponses()).isNullOrEmpty();
	}

	@Test
	@DisabledOnSqlite
	void testCascadeKeepsConcurrentContentEdits() {
		for (var s : List.of("a", "b", "c", "d")) saveSource(URL + s);
		var child = saveChild(URL + "a", URL + "b", URL + "c", URL + "d");
		meta.sources("", child, null);
		var failure = new AtomicReference<Throwable>();
		var edited = editOtherOnFirstUpdate(URL + "c", URL + "d", failure);

		cascade.cascadeRef("", child);

		assertThat(failure.get()).isNull();
		assertThat(edited.get()).isNotNull();
		assertEdited(edited.get(), "");
		assertThat(find(edited.get(), "").getMetadata().getResponses()).containsExactly(URL + "child");
	}

	@Test
	void testHardDeleteRegenKeepsConcurrentContentEdits() {
		saveSource(URL + "a", URL + "child");
		saveSource(URL + "d");
		var surviving = existingChild(URL + "a", URL + "d");
		surviving.setOrigin("@sub");
		surviving.setMetadata(Metadata.builder().regen(true).build());
		refRepository.save(surviving);
		var existing = existingChild(URL + "a", URL + "b", URL + "c");
		existing.setMetadata(Metadata.builder().modified("2026-01-02T00:00:00Z").build());
		var edited = new AtomicBoolean();
		doAnswer(invocation -> {
			Ref ref = invocation.getArgument(0);
			if (!edited.get() && !ref.getUrl().equals(URL + "child")) {
				edited.set(true);
				edit(URL + "child", "@sub");
			}
			return null;
		}).when(mockMessages).updateMetadata(any());

		meta.sources("", null, existing);

		assertThat(edited).isTrue();
		assertEdited(URL + "child", "@sub");
		var saved = find(URL + "child", "@sub");
		assertThat(saved.getMetadata().isCascade()).isTrue();
		assertThat(saved.getMetadata().isRegen()).isFalse();
		assertThat(saved.getMetadata().getModified()).isEqualTo("2026-01-02T00:00:00Z");
	}

	@Test
	void testMetadataWriteDoesNotChangeModified() {
		for (var s : List.of("a", "b", "c", "d")) saveSource(URL + s);
		saveSource(URL + "x", URL + "child");
		var before = new ArrayList<Instant>();
		for (var s : List.of("a", "b", "c", "d", "x")) before.add(find(URL + s, "").getModified());
		var child = saveChild(URL + "a", URL + "b", URL + "c", URL + "d");

		meta.sources("", child, null);
		cascade.cascadeRef("", child);
		var updated = saveChild(URL + "c");
		meta.sources("", updated, existingChild(URL + "a", URL + "b", URL + "c", URL + "d"));
		cascade.cascadeRef("", updated);

		var after = new ArrayList<Instant>();
		for (var s : List.of("a", "b", "c", "d", "x")) after.add(find(URL + s, "").getModified());
		assertThat(after).isEqualTo(before);
		assertThat(find(URL + "c", "").getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(find(URL + "a", "").getMetadata().getResponses()).isNullOrEmpty();
		assertThat(find(URL + "d", "").getMetadata().getResponses()).isNullOrEmpty();
		assertThat(find(URL + "x", "").getMetadata().getResponses()).isNullOrEmpty();
	}

	@Test
	@DisabledOnSqlite
	void testCascadeDoesNotHoldOneLongTransaction() {
		for (var s : List.of("a", "b", "c", "d", "e")) saveSource(URL + s);
		var child = saveChild(URL + "a", URL + "b", URL + "c", URL + "d", URL + "e");
		meta.sources("", child, null);
		var cascaded = new CopyOnWriteArrayList<String>();
		var failure = new AtomicReference<Throwable>();
		doAnswer(invocation -> {
			Ref ref = invocation.getArgument(0);
			cascaded.add(ref.getUrl());
			if (cascaded.size() == 2) {
				try {
					editConcurrently(cascaded.getFirst(), "");
				} catch (Exception e) {
					failure.set(e);
				}
			}
			return null;
		}).when(mockMessages).updateMetadata(any());

		cascade.cascadeRef("", child);

		assertThat(cascaded).hasSize(3);
		assertThat(failure.get()).isNull();
		assertEdited(cascaded.getFirst(), "");
		assertThat(find(cascaded.getFirst(), "").getMetadata().getResponses()).containsExactly(URL + "child");
	}
}
