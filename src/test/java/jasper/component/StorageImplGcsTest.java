package jasper.component;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import jasper.config.Config.GcsRoute;
import jasper.config.Config.ServerConfig;
import jasper.config.Props;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
		storage = gcs(LocalStorageHelper.customOptions(false).getService(), tmpDir.resolve("gcs"), route("public", List.of("cache"), List.of(), "https://cdn.example.com/"));
	}

	static StorageImplGcs gcs(com.google.cloud.storage.Storage client, Path tmpDir, GcsRoute... routes) {
		var gcs = new StorageImplGcs(client, mock(ConfigCache.class), mock(StorageImplLocal.class));
		gcs.tmpDir = tmpDir;
		gcs.update(root(routes));
		return gcs;
	}

	static ServerConfig root(GcsRoute... routes) {
		return ServerConfig.builder()
			.gcsBucket("private")
			.gcsRoutes(List.of(routes))
			.build();
	}

	static GcsRoute route(String bucket, List<String> namespaces, List<String> tenants, String cdnBaseUrl) {
		var route = new GcsRoute();
		route.setBucket(bucket);
		route.setNamespaces(namespaces);
		route.setTenants(tenants);
		route.setCdnBaseUrl(cdnBaseUrl);
		return route;
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
	void testBucketRouting() throws IOException {
		storage.storeAt("", "cache", "a", "public".getBytes());
		storage.storeAt("", "backups", "b", "private".getBytes());

		assertThat(storage.blobId("", "cache", "a").getBucket()).isEqualTo("public");
		assertThat(storage.blobId("", "backups", "b").getBucket()).isEqualTo("private");
		assertThat(storage.blobId("", "preload", "c").getBucket()).isEqualTo("private");
		assertThat(storage.blobId("", "secrets", "d").getBucket()).isEqualTo("private");
		assertThat(storage.blobId("", "config", "e").getBucket()).isEqualTo("private");
		assertThat(storage.exists("", "backups", "b")).isTrue();
		assertThat(storage.listStorage("", "cache")).containsExactly(new Storage.StorageRef("a", 6));
		assertThat(storage.listStorage("", "backups")).containsExactly(new Storage.StorageRef("b", 7));
	}

	@Test
	void testTenantRouting() {
		var gcs = gcs(LocalStorageHelper.customOptions(false).getService(), tmpDir,
			route("tenant-public", List.of("cache"), List.of("@tenant"), "https://tenant.example.com"),
			route("tenant-private", List.of(), List.of("@tenant"), ""),
			route("public", List.of("cache"), List.of("default"), "https://cdn.example.com"),
			route("archive", List.of("backups"), List.of(), ""));

		assertThat(gcs.blobId("@tenant", "cache", "a").getBucket()).isEqualTo("tenant-public");
		assertThat(gcs.blobId("@tenant", "backups", "b").getBucket()).isEqualTo("tenant-private");
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("public");
		assertThat(gcs.blobId("", "backups", "b").getBucket()).isEqualTo("archive");
		assertThat(gcs.blobId("@other", "cache", "a").getBucket()).isEqualTo("private");
		assertThat(gcs.blobId("@other", "backups", "b").getBucket()).isEqualTo("archive");
		assertThat(gcs.getCdnUrl("@tenant", "cache", "a")).isEqualTo("https://tenant.example.com/@tenant/cache/a");
		assertThat(gcs.getCdnUrl("@other", "cache", "a")).isNull();
	}

	@Test
	void testCdnBucketIsolation() {
		var gcs = gcs(mock(com.google.cloud.storage.Storage.class), tmpDir);
		assertThatThrownBy(() -> gcs.update(root(route("private", List.of("cache"), List.of(), "https://cdn.example.com"))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> gcs.update(root(
			route("public", List.of("cache"), List.of(), "https://cdn.example.com"),
			route("public", List.of("backups"), List.of(), ""))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> gcs.update(root(route("public", List.of(), List.of(), "https://cdn.example.com"))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> gcs.update(root().withGcsBucket(""))).isInstanceOf(IllegalArgumentException.class);
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("private");
	}

	@Test
	void testLocalRouting() throws IOException {
		var local = new StorageImplLocal();
		local.props = new Props();
		local.props.setStorage(tmpDir.resolve("local").toString());
		var gcs = new StorageImplGcs(LocalStorageHelper.customOptions(false).getService(), mock(ConfigCache.class), local);
		gcs.tmpDir = tmpDir;
		gcs.update(root(route("public", List.of("cache"), List.of(), "https://cdn.example.com")).withStorage("local").withGcsBucket(""));

		gcs.storeAt("", "cache", "a", "cache".getBytes());
		try (var zipped = gcs.zipAt("", "backups", "b.zip")) {
			gcs.backup("", "cache", zipped, null);
			zipped.commit();
		}

		assertThat(local.exists("", "cache", "a")).isFalse();
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("public");
		assertThat(gcs.getCdnUrl("", "cache", "a")).isEqualTo("https://cdn.example.com/default/cache/a");
		assertThat(local.exists("", "backups", "b.zip")).isTrue();
		assertThat(gcs.exists("", "backups", "b.zip")).isTrue();
		assertThat(gcs.getCdnUrl("", "backups", "b.zip")).isNull();
		assertThat(gcs.listTenants()).containsExactly("default");
		try (var zipped = gcs.streamZip("", "backups", "b.zip")) {
			assertThat(zipped.in("cache/a").readAllBytes()).isEqualTo("cache".getBytes());
		}
		var localCdn = route("", List.of("cache"), List.of(), "https://cdn.example.com");
		localCdn.setStorage("local");
		assertThatThrownBy(() -> gcs.update(root(localCdn))).isInstanceOf(IllegalArgumentException.class);
		var localBackups = route("", List.of("backups"), List.of(), "");
		localBackups.setStorage("local");
		gcs.update(root(localBackups));
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("private");
		assertThat(gcs.exists("", "backups", "b.zip")).isTrue();
		var localBucket = route("private", List.of("backups"), List.of(), "");
		localBucket.setStorage("local");
		assertThatThrownBy(() -> gcs.update(root(localBucket))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> gcs.update(root().withStorage("s3"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> gcs.update(root().withStorage("local"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testRuntimeConfig() {
		var configs = mock(ConfigCache.class);
		var listener = new AtomicReference<Consumer<ServerConfig>>();
		var initial = new AtomicReference<>(ServerConfig.builder().build());
		doAnswer(i -> {
			listener.set(i.getArgument(0));
			listener.get().accept(initial.get());
			return null;
		}).when(configs).rootUpdate(any());
		var gcs = new StorageImplGcs(mock(com.google.cloud.storage.Storage.class), configs, mock(StorageImplLocal.class));
		gcs.tmpDir = tmpDir;
		// An unconfigured server config must not prevent startup, so it can be fixed at runtime
		gcs.init();
		assertThatThrownBy(() -> gcs.blobId("", "cache", "a")).isInstanceOf(IllegalStateException.class);
		listener.get().accept(root(route("private", List.of("cache"), List.of(), "https://cdn.example.com")));
		assertThatThrownBy(() -> gcs.blobId("", "cache", "a")).isInstanceOf(IllegalStateException.class);

		initial.set(root(route("public", List.of("cache"), List.of(), "https://cdn.example.com")));
		gcs.init();
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("public");
		assertThat(gcs.getCdnUrl("", "cache", "a")).isEqualTo("https://cdn.example.com/default/cache/a");

		listener.get().accept(root(route("private", List.of("cache"), List.of(), "https://cdn.example.com")));
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("public");

		listener.get().accept(root());
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("private");
		assertThat(gcs.getCdnUrl("", "cache", "a")).isNull();
	}

	@Test
	void testServerOverrides() {
		var props = new Props();
		props.getOverride().getServer().setGcsBucket("override");
		props.getOverride().getServer().setGcsRoutes(List.of(route("public", List.of("cache"), List.of(), "https://cdn.example.com")));
		var gcs = gcs(mock(com.google.cloud.storage.Storage.class), tmpDir);
		gcs.update(root().wrap(props));

		assertThat(gcs.blobId("", "backups", "b").getBucket()).isEqualTo("override");
		assertThat(gcs.blobId("", "cache", "a").getBucket()).isEqualTo("public");
	}

	@Test
	void testServerOverrideLocalStorage() throws IOException {
		var local = new StorageImplLocal();
		local.props = new Props();
		local.props.setStorage(tmpDir.resolve("local").toString());
		var gcs = new StorageImplGcs(LocalStorageHelper.customOptions(false).getService(), mock(ConfigCache.class), local);
		gcs.tmpDir = tmpDir;

		var props = new Props();
		props.getOverride().getServer().setStorage("local");
		var wrapped = root().wrap(props);
		assertThat(wrapped.getStorage()).isEqualTo("local");
		assertThat(wrapped.getGcsBucket()).isEmpty();
		gcs.update(wrapped);
		gcs.storeAt("", "backups", "b", "backup".getBytes());
		assertThat(local.exists("", "backups", "b")).isTrue();

		var blank = new Props();
		blank.getOverride().getServer().setGcsBucket("");
		assertThat(root().withStorage("local").wrap(blank).getGcsBucket()).isEmpty();
		assertThat(root().wrap(new Props()).getGcsBucket()).isEqualTo("private");
	}

	@Test
	void testCdnUrl() {
		assertThat(storage.getCdnUrl("@other", "cache", "a")).isEqualTo("https://cdn.example.com/@other/cache/a");
		assertThat(storage.getCdnUrl("", "backups", "b.zip")).isNull();
		assertThat(storage.getCdnUrl("", "secrets", "host_key")).isNull();
		var noCdn = gcs(LocalStorageHelper.customOptions(false).getService(), tmpDir, route("public", List.of("cache"), List.of(), ""));
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
		var gcs = gcs(client, tmpDir, route("public", List.of("cache"), List.of(), ""));

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
		var gcs = gcs(client, tmpDir, route("public", List.of("cache"), List.of(), ""));
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
		var gcs = gcs(client, tmpDir, route("public", List.of("cache"), List.of(), ""));

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
		var gcs = gcs(client, tmpDir, route("public", List.of("cache"), List.of(), ""));

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
