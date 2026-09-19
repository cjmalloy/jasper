package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static jasper.repository.spec.RefSpec.isUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@IntegrationTest
@ExtendWith(MockitoExtension.class)
public class IngestIT {

	@InjectMocks
	@Autowired
	Ingest ingest;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Tagger tagger;

	static final String URL = "https://www.example.com/";
	static final String OTHER_URL = "https://www.example.com/other";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testIngestRef() {
		var ref = new Ref();
		ref.setUrl(URL);

		ingest.create("", ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testCreateDuplicateRefFails() {
		var existing = new Ref();
		existing.setUrl(URL);
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);

		assertThatThrownBy(() -> ingest.create("", ref))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testDoubleIngestRefFails() {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setTitle("First");
		var ref2 = new Ref();
		ref2.setUrl(URL);
		ref2.setTitle("Second");

		ingest.ensureCreateUniqueModified(ref1);
		assertThatThrownBy(() -> ingest.ensureCreateUniqueModified(ref2))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
	}

	@Test
	void testUpdateRef() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTitle("First");
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Second");
		ref.setModified(existing.getModified());

		ingest.update("", ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
	}

	@Test
	void testUpdatePublishedDateWithOlderSource() {
		var parent = Ref.from("https:parent", "@test");
		parent.setPublished(Instant.parse("2025-07-30T00:04:26.000Z"));
		refRepository.save(parent);
		var child = Ref.from("https:child", "@test");
		child.setSources(List.of(parent.getUrl()));
		child.setPublished(Instant.parse("2026-09-18T22:39:46.182Z"));
		child.setCreated(Instant.parse("2026-09-18T22:39:42.055Z"));
		child.setModified(Instant.parse("2026-09-18T23:44:22.151354Z"));
		refRepository.save(child);
		child.setPublished(Instant.parse("2026-09-19T16:39:46.000Z"));

		ingest.update("@test", child);

		assertThat(refRepository.findOneByUrlAndOrigin(child.getUrl(), "@test").orElseThrow().getPublished())
			.isEqualTo(Instant.parse("2026-09-19T16:39:46.000Z"));
		assertThat(refRepository.findOneByUrlAndOrigin(parent.getUrl(), "@test").orElseThrow().getPublished())
			.isEqualTo(parent.getPublished());
	}

	@ParameterizedTest
	@ValueSource(strings = {"+user/tester", "_user/tester"})
	void testUpdatePublishedDateWithGeneratedUserResponse(String user) {
		var parent = Ref.from("https:parent", "@test");
		parent.setPublished(Instant.parse("2025-07-30T00:04:26.000Z"));
		refRepository.save(parent);
		var child = Ref.from("https:child", "@test");
		child.setSources(List.of(parent.getUrl()));
		child.setPublished(Instant.parse("2026-09-18T22:39:46.182Z"));
		child.setCreated(Instant.parse("2026-09-18T22:39:42.055Z"));
		child.setModified(Instant.parse("2026-09-18T23:44:22.151354Z"));
		refRepository.save(child);
		var userResponse = tagger.getResponseRef(user, "@test", child.getUrl());
		assertThat(userResponse.getTags()).contains("internal", user).doesNotContain("plugin/user");
		userResponse.setPublished(Instant.parse("2026-09-18T23:00:00.000Z"));
		refRepository.save(userResponse);
		child = refRepository.findOneByUrlAndOrigin(child.getUrl(), "@test").orElseThrow();
		child.setPublished(Instant.parse("2026-09-19T16:39:46.000Z"));

		ingest.update("@test", child);

		assertThat(refRepository.findOneByUrlAndOrigin(child.getUrl(), "@test").orElseThrow().getPublished())
			.isEqualTo(Instant.parse("2026-09-19T16:39:46.000Z"));
		assertThat(refRepository.findOneByUrlAndOrigin(parent.getUrl(), "@test").orElseThrow().getPublished())
			.isEqualTo(parent.getPublished());
	}

	@Test
	void testUpdatePublishedDateStillConstrainedBySource() {
		var parent = Ref.from("https:parent", "@test");
		parent.setPublished(Instant.parse("2025-07-30T00:04:26.000Z"));
		refRepository.save(parent);
		var child = Ref.from("https:child", "@test");
		child.setSources(List.of(parent.getUrl()));
		child.setPublished(Instant.parse("2026-09-18T22:39:46.182Z"));
		refRepository.save(child);
		child.setPublished(parent.getPublished().minusSeconds(3600));

		ingest.update("@test", child);

		assertThat(refRepository.findOneByUrlAndOrigin(child.getUrl(), "@test").orElseThrow().getPublished())
			.isEqualTo(parent.getPublished().plusMillis(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"public", "internal"})
	void testUpdatePublishedDateStillConstrainedByRealResponse(String tag) {
		var parent = Ref.from("https:parent", "@test");
		parent.setPublished(Instant.parse("2025-07-30T00:04:26.000Z"));
		refRepository.save(parent);
		var child = Ref.from("https:child", "@test");
		child.setSources(List.of(parent.getUrl()));
		child.setPublished(Instant.parse("2026-09-18T22:39:46.182Z"));
		refRepository.save(child);
		var reply = Ref.from("https:reply", "@test", tag, "+user/tester");
		reply.setSources(List.of(child.getUrl()));
		reply.setPublished(Instant.parse("2026-09-18T23:00:00.000Z"));
		refRepository.save(reply);
		child.setPublished(Instant.parse("2026-09-19T16:39:46.000Z"));

		ingest.update("@test", child);

		assertThat(refRepository.findOneByUrlAndOrigin(child.getUrl(), "@test").orElseThrow().getPublished())
			.isEqualTo(reply.getPublished().minusMillis(1));
	}

	@Test
	void testDuplicateCreateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		setField(ingest, "ensureUniqueModifiedClock", fixedClock);
		try {
			var ref1 = new Ref();
			ref1.setUrl(URL);
			ref1.setTitle("First");
			ingest.create("", ref1);
			var ref2 = new Ref();
			ref2.setUrl(OTHER_URL);
			ref2.setTitle("Second");

			assertThatThrownBy(() -> ingest.create("", ref2))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
				.isTrue();
			var fetched1 = refRepository.findOneByUrlAndOrigin(URL, "").get();
			assertThat(fetched1.getTitle())
				.isEqualTo("First");
			assertThat(fetched1.getModified())
				.isEqualTo(Instant.now(fixedClock));
			assertThat(refRepository.existsByUrlAndOrigin(OTHER_URL, ""))
				.isFalse();
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}
	}

	@Test
	void testDuplicateUpdateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		try {
			var ref1 = new Ref();
			ref1.setUrl(URL);
			ref1.setTitle("First");
			ingest.create("", ref1);
			setField(ingest, "ensureUniqueModifiedClock", fixedClock);
			var ref2 = new Ref();
			ref2.setUrl(OTHER_URL);
			ref2.setTitle("Second");
			ingest.create("", ref2);
			var update = new Ref();
			update.setUrl(URL);
			update.setTitle("Modified");
			update.setModified(ref1.getModified());

			assertThatThrownBy(() -> ingest.update("", update))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
				.isTrue();
			var fetched1 = refRepository.findOneByUrlAndOrigin(URL, "").get();
			assertThat(fetched1.getTitle())
				.isEqualTo("First");
			var fetched2 = refRepository.findOneByUrlAndOrigin(OTHER_URL, "").get();
			assertThat(fetched2.getTitle())
				.isEqualTo("Second");
			assertThat(fetched2.getModified())
				.isEqualTo(Instant.now(fixedClock));
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}
	}

	@Test
	void testModifiedOptimisticLock() throws InterruptedException {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ingest.create("", ref);
		var update1 = new Ref();
		update1.setUrl(URL);
		update1.setTitle("M1");
		update1.setModified(ref.getModified());
		var update2 = new Ref();
		update2.setUrl(URL);
		update2.setTitle("M2");
		update2.setModified(ref.getModified());

		ingest.update("", update1);
		Thread.sleep(1);
		assertThatThrownBy(() -> ingest.update("", update2))
			.isInstanceOf(ModifiedException.class);
	}

	@Test
	void testConcurrentModifiedOptimisticLock() throws InterruptedException {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ingest.create("", ref);
		var update1 = new Ref();
		update1.setUrl(URL);
		update1.setTitle("M1");
		update1.setModified(ref.getModified());
		var update2 = new Ref();
		update2.setUrl(URL);
		update2.setTitle("M2");
		update2.setModified(ref.getModified());

		AtomicBoolean firstException = new AtomicBoolean(false);
		AtomicBoolean secondException = new AtomicBoolean(false);

		var thread1 = new Thread(() -> {
			try {
				ingest.update("", update1);
			} catch (ModifiedException t) {
				firstException.set(true);
			}
		});
		var thread2 = new Thread(() -> {
			try {
				ingest.update("", update2);
			} catch (ModifiedException t) {
				secondException.set(true);
			}
		});
		thread1.start();
		thread2.start();
		thread1.join();
		thread2.join();

		assertThat(firstException.get())
			.isNotEqualTo(secondException.get());
	}
	@Test
	void testSetObsoleteOnCreate() throws InterruptedException {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setOrigin("@origin1");
		ref1.setTitle("First");
		ingest.create("", ref1);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "@origin1")).get().extracting(r -> r.getMetadata().isObsolete()).isNotEqualTo(true);

		TimeUnit.MILLISECONDS.sleep(1);

		var ref2 = new Ref();
		ref2.setUrl(URL);
		ref2.setOrigin("@origin2");
		ref2.setTitle("Second");
		ingest.create("", ref2);

		var fetched1 = refRepository.findOneByUrlAndOrigin(URL, "@origin1").get();
		var fetched2 = refRepository.findOneByUrlAndOrigin(URL, "@origin2").get();
		assertThat(fetched1.getMetadata().isObsolete()).isTrue();
		assertThat(fetched2.getMetadata().isObsolete()).isFalse();

		TimeUnit.MILLISECONDS.sleep(1);

		var ref3 = new Ref();
		ref3.setUrl(URL);
		ref3.setOrigin("@origin3");
		ref3.setTitle("Third");
		ingest.create("", ref3);

		fetched1 = refRepository.findOneByUrlAndOrigin(URL, "@origin1").get();
		fetched2 = refRepository.findOneByUrlAndOrigin(URL, "@origin2").get();
		var fetched3 = refRepository.findOneByUrlAndOrigin(URL, "@origin3").get();
		assertThat(fetched1.getMetadata().isObsolete()).isTrue();
		assertThat(fetched2.getMetadata().isObsolete()).isTrue();
		assertThat(fetched3.getMetadata().isObsolete()).isFalse();
	}

	@Test
	void testSetObsoleteOnUpdate() throws InterruptedException {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setOrigin("@origin1");
		ref1.setTitle("First");
		ingest.create("", ref1);

		TimeUnit.MILLISECONDS.sleep(1);

		var ref2 = new Ref();
		ref2.setUrl(URL);
		ref2.setOrigin("@origin2");
		ref2.setTitle("Second");
		ingest.create("", ref2);

		var fetched1 = refRepository.findOneByUrlAndOrigin(URL, "@origin1").get();
		var fetched2 = refRepository.findOneByUrlAndOrigin(URL, "@origin2").get();
		assertThat(fetched1.getMetadata().isObsolete()).isTrue();
		assertThat(fetched2.getMetadata().isObsolete()).isFalse();

		TimeUnit.MILLISECONDS.sleep(1);

		var update = new Ref();
		update.setUrl(URL);
		update.setOrigin("@origin1");
		update.setTitle("First Updated");
		update.setModified(fetched1.getModified());
		ingest.update("", update);

		var fetched1Updated = refRepository.findOneByUrlAndOrigin(URL, "@origin1").get();
		fetched2 = refRepository.findOneByUrlAndOrigin(URL, "@origin2").get();
		assertThat(fetched1Updated.getMetadata().isObsolete()).isFalse();
		assertThat(fetched2.getMetadata().isObsolete()).isTrue();
	}

	@Test
	void testSetObsoleteOnDelete() throws InterruptedException {
		var ref1 = new Ref();
		ref1.setUrl(URL);
		ref1.setOrigin("@origin1");
		ref1.setTitle("First");
		ingest.create("", ref1);

		TimeUnit.MILLISECONDS.sleep(1);

		var ref2 = new Ref();
		ref2.setUrl(URL);
		ref2.setOrigin("@origin2");
		ref2.setTitle("Second");
		ingest.create("", ref2);

		var fetched1 = refRepository.findOneByUrlAndOrigin(URL, "@origin1").get();
		var fetched2 = refRepository.findOneByUrlAndOrigin(URL, "@origin2").get();
		assertThat(fetched1.getMetadata().isObsolete()).isTrue();
		assertThat(fetched2.getMetadata().isObsolete()).isFalse();

		ingest.delete("", URL, "@origin2");

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@origin2")).isFalse();
		fetched1 = refRepository.findOneByUrlAndOrigin(URL, "@origin1").get();
		assertThat(fetched1.getMetadata().isObsolete()).isFalse();
	}

	@Test
	void testConcurrentUpdate_shouldResultInOneCurrentRef() {
		var refOriginA = new Ref();
		refOriginA.setUrl(URL);
		refOriginA.setOrigin("@a");
		refOriginA.setTitle("First");
		var refOriginB = new Ref();
		refOriginB.setUrl(URL);
		refOriginB.setOrigin("@b");
		refOriginB.setTitle("Second");
		refRepository.saveAll(List.of(refOriginA, refOriginB));

		// Simulate clock skew
		Instant timeA = Instant.now();
		Instant timeB = timeA.minus(100, ChronoUnit.MILLIS);

		var latestA = refRepository.findOneByUrlAndOrigin(refOriginA.getUrl(), refOriginA.getOrigin()).get();
		latestA.setComment("...move A...");
		setField(ingest, "ensureUniqueModifiedClock", Clock.fixed(timeA, ZoneOffset.UTC));
		try {
			ingest.update("", latestA);
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}

		Ref latestB = refRepository.findOneByUrlAndOrigin(refOriginB.getUrl(), refOriginB.getOrigin()).get();
		latestB.setComment("...move B...");
		setField(ingest, "ensureUniqueModifiedClock", Clock.fixed(timeB, ZoneOffset.UTC));
		try {
			ingest.update("", latestB);
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}

		List<Ref> allVersions = refRepository.findAll(isUrl(refOriginA.getUrl()));
		long activeRefs = allVersions.stream().filter(r -> r.getMetadata() == null || !r.getMetadata().isObsolete()).count();
		assertEquals(1, activeRefs, "There should be exactly one non-obsolete Ref after concurrent updates.");
	}

	@Test
	void testConcurrentUpdatePush_shouldResultInOneCurrentRef() {
		var refOriginA = new Ref();
		refOriginA.setUrl(URL);
		refOriginA.setOrigin("@a");
		refOriginA.setTitle("First");
		var refOriginB = new Ref();
		refOriginB.setUrl(URL);
		refOriginB.setOrigin("@b");
		refOriginB.setTitle("Second");
		refRepository.saveAll(List.of(refOriginA, refOriginB));

		// Simulate clock skew
		Instant timeA = Instant.now();
		Instant timeB = timeA.minus(100, ChronoUnit.MILLIS);

		var latestA = refRepository.findOneByUrlAndOrigin(refOriginA.getUrl(), refOriginA.getOrigin()).get();
		latestA.setComment("...move A...");
		setField(ingest, "ensureUniqueModifiedClock", Clock.fixed(timeA, ZoneOffset.UTC));
		try {
			ingest.update("", latestA);
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}

		Ref latestB = refRepository.findOneByUrlAndOrigin(refOriginB.getUrl(), refOriginB.getOrigin()).get();
		latestB.setComment("...move B...");
		latestB.setModified(timeB);
		ingest.push("", latestB, false, false);

		List<Ref> allVersions = refRepository.findAll(isUrl(refOriginA.getUrl()));
		long activeRefs = allVersions.stream().filter(r -> r.getMetadata() == null || !r.getMetadata().isObsolete()).count();
		assertEquals(1, activeRefs, "There should be exactly one non-obsolete Ref after concurrent updates.");
	}

	@Test
	void testConcurrentPushUpdate_shouldResultInOneCurrentRef() {
		var refOriginA = new Ref();
		refOriginA.setUrl(URL);
		refOriginA.setOrigin("@a");
		refOriginA.setTitle("First");
		var refOriginB = new Ref();
		refOriginB.setUrl(URL);
		refOriginB.setOrigin("@b");
		refOriginB.setTitle("Second");
		refRepository.saveAll(List.of(refOriginA, refOriginB));

		// Simulate clock skew
		Instant timeA = Instant.now();
		Instant timeB = timeA.minus(100, ChronoUnit.MILLIS);

		Ref latestA = refRepository.findOneByUrlAndOrigin(refOriginA.getUrl(), refOriginA.getOrigin()).get();
		latestA.setComment("...move A...");
		latestA.setModified(timeA);
		ingest.push("", latestA, false, false);

		Ref latestB = refRepository.findOneByUrlAndOrigin(refOriginB.getUrl(), refOriginB.getOrigin()).get();
		latestB.setComment("...move B...");
		setField(ingest, "ensureUniqueModifiedClock", Clock.fixed(timeB, ZoneOffset.UTC));
		try {
			ingest.update("", latestB);
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}

		List<Ref> allVersions = refRepository.findAll(isUrl(refOriginA.getUrl()));
		long activeRefs = allVersions.stream().filter(r -> r.getMetadata() == null || !r.getMetadata().isObsolete()).count();
		assertEquals(1, activeRefs, "There should be exactly one non-obsolete Ref after concurrent updates.");
	}

	@Test
	void testConcurrentPush_shouldResultInOneCurrentRef() {
		var refOriginA = new Ref();
		refOriginA.setUrl(URL);
		refOriginA.setOrigin("@a");
		refOriginA.setTitle("First");
		var refOriginB = new Ref();
		refOriginB.setUrl(URL);
		refOriginB.setOrigin("@b");
		refOriginB.setTitle("Second");
		refRepository.saveAll(List.of(refOriginA, refOriginB));

		// Simulate clock skew
		Instant timeA = Instant.now();
		Instant timeB = timeA.minus(100, ChronoUnit.MILLIS);

		Ref latestA = refRepository.findOneByUrlAndOrigin(refOriginA.getUrl(), refOriginA.getOrigin()).get();
		latestA.setComment("...move A...");
		latestA.setModified(timeA);
		ingest.push("", latestA, false, false);

		Ref latestB = refRepository.findOneByUrlAndOrigin(refOriginB.getUrl(), refOriginB.getOrigin()).get();
		latestB.setComment("...move B...");
		latestB.setModified(timeB);
		ingest.push("", latestB, false, false);

		List<Ref> allVersions = refRepository.findAll(isUrl(refOriginA.getUrl()));
		long activeRefs = allVersions.stream().filter(r -> r.getMetadata() == null || !r.getMetadata().isObsolete()).count();
		assertEquals(1, activeRefs, "There should be exactly one non-obsolete Ref after concurrent updates.");
	}

	@Test
	void testUpdateResponse() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTitle("First");
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Second");
		ref.setModified(existing.getModified());

		ingest.updateResponse("", ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
	}

	@Test
	void testUpdateResponseWithTags() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTitle("First");
		existing.setTags(List.of("test/tag"));
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Second");
		ref.setTags(List.of("test/tag", "another/tag"));
		ref.setModified(existing.getModified());

		ingest.updateResponse("", ref);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
		assertThat(fetched.getTags())
			.contains("test/tag", "another/tag");
		assertThat(fetched.getMetadata())
			.isNotNull();
		assertThat(fetched.getMetadata().getExpandedTags())
			.isNotNull();
	}

	@Test
	void testUpdateResponseThrowsNotFoundExceptionWhenRefDoesNotExist() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Test");

		assertThatThrownBy(() -> ingest.updateResponse("", ref))
			.hasMessageContaining("Ref");
	}

	@Test
	void testUpdateResponseWithRngPlugin() {
		var existing = new Ref();
		existing.setUrl(URL);
		existing.setTitle("First");
		existing.setTags(new ArrayList<>(List.of("plugin/rng", "+plugin/rng/uuid1")));
		refRepository.save(existing);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Second");
		ref.setTags(new ArrayList<>(List.of("plugin/rng")));
		ref.setModified(existing.getModified());

		ingest.updateResponse("", ref);

		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("+plugin/rng/uuid1");
	}


}
