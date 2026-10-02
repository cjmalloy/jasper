package jasper.component;

import jasper.config.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StorageImplLocalTest {

	StorageImplLocal storage;

	@TempDir
	Path tmpDir;

	@BeforeEach
	void init() {
		storage = new StorageImplLocal();
		storage.props = new Props();
		storage.props.setStorage(tmpDir.toString());
	}

	@Test
	void testZipPublishedOnCommit() throws IOException {
		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var os = zipped.out("ref.json")) {
				os.write("[]".getBytes());
			}
			zipped.commit();
		}
		assertThat(storage.exists("", "backups", "b.zip")).isTrue();
		assertThat(storage.exists("", "backups", "_b.zip")).isFalse();
	}

	@Test
	void testZipNotPublishedWithoutCommit() throws IOException {
		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var os = zipped.out("ref.json")) {
				os.write("[]".getBytes());
			}
		}
		assertThat(storage.exists("", "backups", "b.zip")).isFalse();
		try (var files = Files.list(storage.dir("", "backups"))) {
			assertThat(files).isEmpty();
		}
	}
}
