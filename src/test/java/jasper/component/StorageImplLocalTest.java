package jasper.component;

import jasper.config.Props;
import jasper.errors.AlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void testStoreAtFailureLeavesNoFile() {
		var failing = new InputStream() {
			int count;
			@Override
			public int read() throws IOException {
				if (count++ > 10) throw new IOException("Transfer failed");
				return 'a';
			}
		};
		assertThatThrownBy(() -> storage.storeAt("", "cache", "abc", failing)).isInstanceOf(IOException.class);
		assertThat(storage.exists("", "cache", "abc")).isFalse();
		assertThat(storage.listStorage("", "cache")).isEmpty();
	}

	@Test
	void testStoreAtExisting() throws IOException {
		storage.storeAt("", "cache", "abc", new ByteArrayInputStream("first".getBytes()));
		assertThatThrownBy(() -> storage.storeAt("", "cache", "abc", new ByteArrayInputStream("second".getBytes())))
			.isInstanceOf(AlreadyExistsException.class);
		assertThat(storage.get("", "cache", "abc")).isEqualTo("first".getBytes());
		assertThat(storage.listStorage("", "cache")).hasSize(1);
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

	@Test
	void testFailedCommitRemovesTemporaryZip() throws IOException {
		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var os = zipped.out("ref.json")) {
				os.write("[]".getBytes());
			}
			// Created concurrently, so publishing fails
			storage.storeAt("", "backups", "b.zip", "other".getBytes());
			assertThatThrownBy(zipped::commit).isInstanceOf(IOException.class);
		}
		assertThat(storage.get("", "backups", "b.zip")).isEqualTo("other".getBytes());
		assertThat(storage.exists("", "backups", "_b.zip")).isFalse();
	}
}
