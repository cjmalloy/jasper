package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.repository.PluginRepository;
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
public class IngestPluginIT {

	@InjectMocks
	@Autowired
	IngestPlugin ingest;

	@Autowired
	PluginRepository pluginRepository;

	@BeforeEach
	void init() {
		pluginRepository.deleteAll();
	}

	@Test
	void testIngestExt() {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");

		ingest.create(plugin);

		assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
			.isTrue();
	}

	@Test
	void testCreateDuplicateExtFails() {
		var existing = new Plugin();
		existing.setTag("plugin/test");
		pluginRepository.save(existing);
		var plugin = new Plugin();
		plugin.setTag("plugin/test");

		assertThatThrownBy(() -> ingest.create(plugin))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
			.isTrue();
	}

	@Test
	void testDoubleIngestExtFails() {
		var ext1 = new Plugin();
		ext1.setTag("plugin/test");
		ext1.setName("First");
		var ext2 = new Plugin();
		ext2.setTag("plugin/test");
		ext2.setName("Second");

		ingest.ensureCreateUniqueModified(ext1);
		assertThatThrownBy(() -> ingest.ensureCreateUniqueModified(ext2))
			.isInstanceOf(AlreadyExistsException.class);

		assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
			.isTrue();
	}

	@Test
	void testUpdateExt() {
		var existing = new Plugin();
		existing.setTag("plugin/test");
		existing.setName("First");
		pluginRepository.save(existing);
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setName("Second");
		plugin.setModified(existing.getModified());

		ingest.update(plugin);

		assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
			.isTrue();
		var fetched = pluginRepository.findOneByQualifiedTag("plugin/test").get();
		assertThat(fetched.getName())
			.isEqualTo("Second");
	}

	@Test
	void testDuplicateCreateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		setField(ingest, "ensureUniqueModifiedClock", fixedClock);
		try {
			var ext1 = new Plugin();
			ext1.setTag("plugin/test");
			ext1.setName("First");
			ingest.create(ext1);
			var ext2 = new Plugin();
			ext2.setTag("plugin/other");
			ext2.setName("Second");

			assertThatThrownBy(() -> ingest.create(ext2))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
				.isTrue();
			var fetched1 = pluginRepository.findOneByQualifiedTag("plugin/test").get();
			assertThat(fetched1.getName())
				.isEqualTo("First");
			assertThat(fetched1.getModified())
				.isEqualTo(Instant.now(fixedClock));
			assertThat(pluginRepository.existsByQualifiedTag("plugin/other"))
				.isFalse();
		} finally {
			setField(ingest, "ensureUniqueModifiedClock", Clock.systemUTC());
		}
	}

	@Test
	void testDuplicateUpdateModifiedFails() {
		var fixedClock = Clock.fixed(Instant.ofEpochSecond(1640000000), ZoneOffset.UTC);
		try {
			var ext1 = new Plugin();
			ext1.setTag("plugin/test");
			ext1.setName("First");
			ingest.create(ext1);
			setField(ingest, "ensureUniqueModifiedClock", fixedClock);
			var ext2 = new Plugin();
			ext2.setTag("plugin/other");
			ext2.setName("Second");
			ingest.create(ext2);
			var update = new Plugin();
			update.setTag("plugin/test");
			update.setName("Modified");
			update.setModified(ext1.getModified());

			assertThatThrownBy(() -> ingest.update(update))
				.isInstanceOf(DuplicateModifiedDateException.class);

			assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
				.isTrue();
			var fetched1 = pluginRepository.findOneByQualifiedTag("plugin/test").get();
			assertThat(fetched1.getName())
				.isEqualTo("First");
			var fetched2 = pluginRepository.findOneByQualifiedTag("plugin/other").get();
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
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setName("First");
		ingest.create(plugin);
		var update1 = new Plugin();
		update1.setTag("plugin/test");
		update1.setName("M1");
		update1.setModified(plugin.getModified());
		var update2 = new Plugin();
		update2.setTag("plugin/test");
		update2.setName("M2");
		update2.setModified(plugin.getModified());

		ingest.update(update1);
		Thread.sleep(1);
		assertThatThrownBy(() -> ingest.update(update2))
			.isInstanceOf(ModifiedException.class);
	}

	@Test
	void testConcurrentModifiedOptimisticLock() throws InterruptedException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setName("First");
		ingest.create(plugin);
		var update1 = new Plugin();
		update1.setTag("plugin/test");
		update1.setName("M1");
		update1.setModified(plugin.getModified());
		var update2 = new Plugin();
		update2.setTag("plugin/test");
		update2.setName("M2");
		update2.setModified(plugin.getModified());

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
