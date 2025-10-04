package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Template;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.repository.TemplateRepository;
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
public class IngestTemplateIT {

	@InjectMocks
	@Autowired
	IngestTemplate ingest;

	@Autowired
	TemplateRepository templateRepository;

	@BeforeEach
	void init() {
		templateRepository.deleteAll();
	}

	@Test
	void testIngestExt() {
		var template = new Template();
		template.setTag("test");

		ingest.create(template);

		assertThat(templateRepository.existsByQualifiedTag("test"))
			.isTrue();
	}

	@Test
	void testCreateDuplicateExtFails() {
		var existing = new Template();
		existing.setTag("test");
		templateRepository.save(existing);
		var template = new Template();
		template.setTag("test");

		assertThatThrownBy(() -> ingest.create(template))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(templateRepository.existsByQualifiedTag("test"))
			.isTrue();
	}

	@Test
	void testDoubleIngestExtFails() {
		var ext1 = new Template();
		ext1.setTag("test");
		ext1.setName("First");
		var ext2 = new Template();
		ext2.setTag("test");
		ext2.setName("Second");

		ingest.ensureCreateUniqueModified(ext1);
		assertThatThrownBy(() -> ingest.ensureCreateUniqueModified(ext2))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(templateRepository.existsByQualifiedTag("test"))
			.isTrue();
	}

	@Test
	void testUpdateExt() {
		var existing = new Template();
		existing.setTag("test");
		existing.setName("First");
		templateRepository.save(existing);
		var template = new Template();
		template.setTag("test");
		template.setName("Second");
		template.setModified(existing.getModified());

		ingest.update(template);

		assertThat(templateRepository.existsByQualifiedTag("test"))
			.isTrue();
		var fetched = templateRepository.findOneByQualifiedTag("test").get();
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testDuplicateCreateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		setField(ingest, "ensureUniqueModifiedClock", fixedClock);
		try {
			var ext1 = new Template();
			ext1.setTag("test");
			ext1.setName("First");
			ingest.create(ext1);
			var ext2 = new Template();
			ext2.setTag("other");
			ext2.setName("Second");

			assertThatThrownBy(() -> ingest.create(ext2))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(templateRepository.existsByQualifiedTag("test"))
				.isTrue();
			var fetched1 = templateRepository.findOneByQualifiedTag("test").get();
			assertThat(fetched1.getName())
				.isEqualTo("First");
			assertThat(fetched1.getModified())
				.isEqualTo(Instant.now(fixedClock));
			assertThat(templateRepository.existsByQualifiedTag("other"))
				.isFalse();
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}
	}

	@Test
	void testDuplicateUpdateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		try {
			var ext1 = new Template();
			ext1.setTag("test");
			ext1.setName("First");
			ingest.create(ext1);
			setField(ingest, "ensureUniqueModifiedClock", fixedClock);
			var ext2 = new Template();
			ext2.setTag("other");
			ext2.setName("Second");
			ingest.create(ext2);
			var update = new Template();
			update.setTag("test");
			update.setName("Modified");
			update.setModified(ext1.getModified());

			assertThatThrownBy(() -> ingest.update(update))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(templateRepository.existsByQualifiedTag("test"))
				.isTrue();
			var fetched1 = templateRepository.findOneByQualifiedTag("test").get();
			assertThat(fetched1.getName())
				.isEqualTo("First");
			var fetched2 = templateRepository.findOneByQualifiedTag("other").get();
			assertThat(fetched2.getName())
				.isEqualTo("Second");
			assertThat(fetched2.getModified())
				.isEqualTo(Instant.now(fixedClock));
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}
	}

	@Test
	void testModifiedOptimisticLock() throws InterruptedException {
		var template = new Template();
		template.setTag("test");
		template.setName("First");
		ingest.create(template);
		var update1 = new Template();
		update1.setTag("test");
		update1.setName("M1");
		update1.setModified(template.getModified());
		var update2 = new Template();
		update2.setTag("test");
		update2.setName("M2");
		update2.setModified(template.getModified());

		ingest.update(update1);
		Thread.sleep(1);
		assertThatThrownBy(() -> ingest.update(update2))
			.isInstanceOf(ModifiedException.class);
	}

	@Test
	void testConcurrentModifiedOptimisticLock() throws InterruptedException {
		var template = new Template();
		template.setTag("test");
		template.setName("First");
		ingest.create(template);
		var update1 = new Template();
		update1.setTag("test");
		update1.setName("M1");
		update1.setModified(template.getModified());
		var update2 = new Template();
		update2.setTag("test");
		update2.setName("M2");
		update2.setModified(template.getModified());

		AtomicBoolean firstException = new AtomicBoolean(false);
		AtomicBoolean secondException = new AtomicBoolean(false);

		var thread1 = new Thread(() -> {
			try {
				ingest.update(update1);
			} catch (ModifiedException t) {
				firstException.set(true);
			}
		});
		var thread2 = new Thread(() -> {
			try {
				ingest.update(update2);
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
