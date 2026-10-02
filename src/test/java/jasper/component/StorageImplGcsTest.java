package jasper.component;

import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import jasper.errors.AlreadyExistsException;
import jasper.errors.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StorageImplGcsTest {

	StorageImplGcs storage;

	@BeforeEach
	void init() {
		storage = new StorageImplGcs(LocalStorageHelper.customOptions(false).getService(), "bucket");
	}

	@Test
	void testStoreAndGet() throws IOException {
		storage.storeAt("", "cache", "a", "hello".getBytes());

		assertThat(storage.exists("", "cache", "a")).isTrue();
		assertThat(storage.get("", "cache", "a")).isEqualTo("hello".getBytes());
		assertThat(storage.size("", "cache", "a")).isEqualTo(5);
		assertThat(storage.blobId("", "cache", "a").getName()).isEqualTo("default/cache/a");
	}

	@Test
	void testMissing() {
		assertThat(storage.exists("", "cache", "missing")).isFalse();
		assertThat(storage.size("", "cache", "missing")).isZero();
		assertThatThrownBy(() -> storage.get("", "cache", "missing")).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> storage.stream("", "cache", "missing")).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> storage.delete("", "cache", "missing")).isInstanceOf(NoSuchFileException.class);
		assertThatThrownBy(() -> storage.overwrite("", "cache", "missing", new byte[0])).isInstanceOf(NotFoundException.class);
	}

	@Test
	void testSanitize() {
		assertThatThrownBy(() -> storage.get("", "cache", "../a")).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> storage.listStorage("", "ca/che")).isInstanceOf(NotFoundException.class);
	}

	@Test
	void testStoreAtExisting() throws IOException {
		storage.storeAt("", "cache", "a", "hello".getBytes());

		assertThatThrownBy(() -> storage.storeAt("", "cache", "a", "x".getBytes())).isInstanceOf(AlreadyExistsException.class);
		assertThatThrownBy(() -> storage.storeAt("", "cache", "a", new ByteArrayInputStream("x".getBytes()))).isInstanceOf(AlreadyExistsException.class);
	}

	@Test
	void testStreams() throws IOException {
		var id = storage.store("@other", "cache", new ByteArrayInputStream("streamed".getBytes()));

		try (var is = storage.stream("@other", "cache", id)) {
			assertThat(new String(is.readAllBytes(), UTF_8)).isEqualTo("streamed");
		}
		var os = new ByteArrayOutputStream();
		assertThat(storage.stream("@other", "cache", id, os)).isEqualTo(8);
		assertThat(os.toString(UTF_8)).isEqualTo("streamed");
	}

	@Test
	void testOverwriteAndDelete() throws IOException {
		var id = storage.store("", "cache", "one".getBytes());
		storage.overwrite("", "cache", id, "two".getBytes());
		assertThat(storage.get("", "cache", id)).isEqualTo("two".getBytes());

		storage.delete("", "cache", id);
		assertThat(storage.exists("", "cache", id)).isFalse();
	}

	@Test
	void testListing() throws IOException {
		storage.storeAt("", "cache", "a", "1".getBytes());
		storage.storeAt("", "cache", "b", "22".getBytes());
		storage.storeAt("", "backups", "c", "333".getBytes());
		storage.storeAt("@other", "cache", "d", "4444".getBytes());

		assertThat(storage.listTenants()).containsExactlyInAnyOrder("default", "@other");
		var origins = new java.util.ArrayList<String>();
		storage.visitTenants(origins::add);
		assertThat(origins).containsExactlyInAnyOrder("", "@other");
		assertThat(storage.listStorage("", "cache")).containsExactlyInAnyOrder(
			new Storage.StorageRef("a", 1),
			new Storage.StorageRef("b", 2));
		var ids = new java.util.ArrayList<String>();
		storage.visitStorage("@other", "cache", ids::add);
		assertThat(ids).containsExactly("d");
		assertThat(storage.listStorage("", "missing")).isEmpty();
	}

	@Test
	void testZipRoundTripWithBackupAndRestore() throws IOException {
		storage.storeAt("", "cache", "a", "cached".getBytes());

		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var os = zipped.out("ref.json")) {
				os.write("[]".getBytes());
			}
			storage.backup("", "cache", zipped, null);
			assertThat(storage.exists("", "backups", "b.zip")).isFalse();
		}
		assertThat(storage.exists("", "backups", "b.zip")).isTrue();
		assertThatThrownBy(() -> storage.zipAt("", "backups", "b.zip")).isInstanceOf(AlreadyExistsException.class);

		try (var zipped = storage.streamZip("", "backups", "b.zip")) {
			try (var is = zipped.in("ref.json")) {
				assertThat(new String(is.readAllBytes(), UTF_8)).isEqualTo("[]");
			}
			assertThat(zipped.list("ref.*\\.json")).hasNext();
			assertThat(Files.exists(zipped.get("cache", "a"))).isTrue();
			storage.restore("@restored", "cache", zipped);
		}
		assertThat(storage.get("@restored", "cache", "a")).isEqualTo("cached".getBytes());
	}

	@Test
	void testBackupModifiedAfter() throws IOException {
		storage.storeAt("", "cache", "a", "cached".getBytes());

		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			storage.backup("", "cache", zipped, Instant.now().plusSeconds(60));
			assertThat(Files.exists(zipped.get("cache", "a"))).isFalse();
		}
	}

	@Test
	void testStreamZipMissing() {
		assertThatThrownBy(() -> storage.streamZip("", "backups", "missing.zip")).isInstanceOf(NoSuchFileException.class);
	}
}
