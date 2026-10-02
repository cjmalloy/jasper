package jasper.component;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StorageImplGcsTest {

	StorageImplGcs storage;

	@TempDir
	Path tmpDir;

	@BeforeEach
	void init() {
		storage = new StorageImplGcs(LocalStorageHelper.customOptions(false).getService(), "public", "https://cdn.example.com/", tmpDir.resolve("gcs"));
	}

	@Test
	void testStoreAndGet() throws IOException {
		storage.storeAt("", "cache", "a", "hello".getBytes());

		assertThat(storage.exists("", "cache", "a")).isTrue();
		assertThat(storage.get("", "cache", "a")).isEqualTo("hello".getBytes());
		assertThat(storage.size("", "cache", "a")).isEqualTo(5);
		assertThat(storage.blobId("", "cache", "a").getName()).isEqualTo("default/cache/a");
		assertThat(storage.blobId("", "cache", "a").getBucket()).isEqualTo("public");
	}

	@Test
	void testBucketRequired() {
		assertThatThrownBy(() -> new StorageImplGcs(mock(com.google.cloud.storage.Storage.class), "", "", tmpDir))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testCdnUrl() {
		assertThat(storage.getCdnUrl("@other", "cache", "a")).isEqualTo("https://cdn.example.com/@other/cache/a");
		var noCdn = new StorageImplGcs(LocalStorageHelper.customOptions(false).getService(), "private", "", tmpDir);
		assertThat(noCdn.getCdnUrl("", "cache", "a")).isNull();
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
	void testStoreAtBytesConcurrentlyCreated() {
		var client = mock(com.google.cloud.storage.Storage.class);
		when(client.create(any(BlobInfo.class), any(byte[].class), any(com.google.cloud.storage.Storage.BlobTargetOption[].class)))
			.thenThrow(new StorageException(412, "Precondition Failed"));
		var gcs = new StorageImplGcs(client, "public", "", tmpDir);

		assertThatThrownBy(() -> gcs.storeAt("", "cache", "a", "x".getBytes())).isInstanceOf(AlreadyExistsException.class);
	}

	@Test
	void testFailedSourceIsNotFinalized() throws IOException {
		var client = mock(com.google.cloud.storage.Storage.class);
		var writer = mock(WriteChannel.class);
		when(client.writer(any(BlobInfo.class), any(com.google.cloud.storage.Storage.BlobWriteOption[].class))).thenReturn(writer);
		when(writer.isOpen()).thenReturn(true);
		when(writer.write(any(ByteBuffer.class))).thenAnswer(i -> {
			var buf = i.getArgument(0, ByteBuffer.class);
			var n = buf.remaining();
			buf.position(buf.limit());
			return n;
		});
		var gcs = new StorageImplGcs(client, "public", "", tmpDir);
		var failing = new InputStream() {
			int count = 0;
			@Override
			public int read() throws IOException {
				if (count++ < 1024) return 'x';
				throw new IOException("source failed");
			}
		};

		assertThatThrownBy(() -> gcs.storeAt("", "cache", "a", failing)).isInstanceOf(IOException.class);
		verify(writer, never()).close();
	}

	@Test
	void testStreamPropagatesServiceErrors() throws IOException {
		var client = mock(com.google.cloud.storage.Storage.class);
		var blob = mock(Blob.class);
		var reader = mock(ReadChannel.class);
		when(client.get(any(BlobId.class))).thenReturn(blob);
		when(blob.reader()).thenReturn(reader);
		when(reader.read(any(ByteBuffer.class))).thenThrow(new StorageException(403, "Forbidden"));
		var gcs = new StorageImplGcs(client, "public", "", tmpDir);

		assertThatThrownBy(() -> gcs.stream("", "cache", "a", new ByteArrayOutputStream()))
			.isInstanceOf(StorageException.class)
			.isNotInstanceOf(NotFoundException.class);
	}

	@Test
	void testOverwriteConflict() {
		var client = mock(com.google.cloud.storage.Storage.class);
		var blob = mock(Blob.class);
		var blobId = BlobId.of("public", "default/cache/a");
		when(client.get(eq(blobId), any(com.google.cloud.storage.Storage.BlobGetOption[].class))).thenReturn(blob);
		when(blob.getBlobId()).thenReturn(blobId);
		when(blob.getGeneration()).thenReturn(1L);
		when(client.create(any(BlobInfo.class), any(byte[].class), any(com.google.cloud.storage.Storage.BlobTargetOption[].class)))
			.thenThrow(new StorageException(412, "Precondition Failed"));
		var gcs = new StorageImplGcs(client, "public", "", tmpDir);

		assertThatThrownBy(() -> gcs.overwrite("", "cache", "a", "x".getBytes())).isInstanceOf(ModifiedException.class);
	}

	@Test
	void testZipUsesConfiguredTmpDir() throws IOException {
		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var files = Files.list(tmpDir.resolve("gcs"))) {
				assertThat(files).singleElement().satisfies(p -> assertThat(p.getFileName().toString()).startsWith("jasper-gcs-"));
			}
			zipped.commit();
		}
		try (var files = Files.list(tmpDir.resolve("gcs"))) {
			assertThat(files).isEmpty();
		}
		assertThat(storage.exists("", "backups", "b.zip")).isTrue();
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
		storage.storeAt("@private", "backups", "e", "5".getBytes());

		assertThat(storage.listTenants()).containsExactlyInAnyOrder("default", "@other", "@private");
		var origins = new java.util.ArrayList<String>();
		storage.visitTenants(origins::add);
		assertThat(origins).containsExactlyInAnyOrder("", "@other", "@private");
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
			zipped.commit();
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
	void testZipNotUploadedWithoutCommit() throws IOException {
		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var os = zipped.out("ref.json")) {
				os.write("[]".getBytes());
			}
		}
		assertThat(storage.exists("", "backups", "b.zip")).isFalse();
		try (var files = Files.list(tmpDir.resolve("gcs"))) {
			assertThat(files).isEmpty();
		}
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
