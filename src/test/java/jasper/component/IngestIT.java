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
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@IntegrationTest
@ExtendWith(MockitoExtension.class)
public class IngestIT {

	@InjectMocks
	@Autowired
	Ingest ingest;

	@Autowired
	RefRepository refRepository;

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

		ingest.ingest(ref, false);

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

		assertThatThrownBy(() -> ingest.ingest(ref, false))
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

		ingest.update(ref, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTitle())
			.isEqualTo("Second");
	}

	@Test
	void testDuplicateCreateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		setField(ingest, "ensureUniqueModifiedClock", fixedClock);
		try {
			var ref1 = new Ref();
			ref1.setUrl(URL);
			ref1.setTitle("First");
			ingest.ingest(ref1, false);
			var ref2 = new Ref();
			ref2.setUrl(OTHER_URL);
			ref2.setTitle("Second");

			assertThatThrownBy(() -> ingest.ingest(ref2, false))
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
			ingest.ingest(ref1, false);
			setField(ingest, "ensureUniqueModifiedClock", fixedClock);
			var ref2 = new Ref();
			ref2.setUrl(OTHER_URL);
			ref2.setTitle("Second");
			ingest.ingest(ref2, false);
			var update = new Ref();
			update.setUrl(URL);
			update.setTitle("Modified");
			update.setModified(ref1.getModified());

			assertThatThrownBy(() -> ingest.update(update, false))
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
		ingest.ingest(ref, false);
		var update1 = new Ref();
		update1.setUrl(URL);
		update1.setTitle("M1");
		update1.setModified(ref.getModified());
		var update2 = new Ref();
		update2.setUrl(URL);
		update2.setTitle("M2");
		update2.setModified(ref.getModified());

		ingest.update(update1, false);
		Thread.sleep(1);
		assertThatThrownBy(() -> ingest.update(update2, false))
			.isInstanceOf(ModifiedException.class);
	}

	@Test
	void testConcurrentModifiedOptimisticLock() throws InterruptedException {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ingest.ingest(ref, false);
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
				ingest.update(update1, false);
			} catch (ModifiedException t) {
				firstException.set(true);
			}
		});
		var thread2 = new Thread(() -> {
			try {
				ingest.update(update2, false);
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

}
