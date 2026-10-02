package jasper.component;

import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StorageImplS3Test {

	FakeS3 s3;
	StorageImplS3 storage;

	@TempDir
	Path tmpDir;

	@BeforeEach
	void init() {
		s3 = new FakeS3();
		storage = new StorageImplS3(s3, "public", "https://cdn.example.com/", tmpDir.resolve("s3"));
	}

	@Test
	void testStoreAndGet() throws IOException {
		storage.storeAt("", "cache", "a", "hello".getBytes());

		assertThat(storage.exists("", "cache", "a")).isTrue();
		assertThat(storage.get("", "cache", "a")).isEqualTo("hello".getBytes());
		assertThat(storage.size("", "cache", "a")).isEqualTo(5);
		assertThat(storage.location("", "cache", "a").key()).isEqualTo("default/cache/a");
		assertThat(storage.location("", "cache", "a").bucket()).isEqualTo("public");
	}

	@Test
	void testPublicMetadata() throws IOException {
		storage.storeAt("", "cache", "a.png", "png".getBytes());
		storage.storeAt("", "cache", "b", new ByteArrayInputStream("bin".getBytes()));
		new StorageImplS3(s3, "private", "", tmpDir).storeAt("", "backups", "c.zip", "zip".getBytes());

		assertThat(s3.objects("public").get("default/cache/a.png").contentType).isEqualTo("image/png");
		assertThat(s3.objects("public").get("default/cache/a.png").contentDisposition).isEqualTo("inline");
		assertThat(s3.objects("public").get("default/cache/b").contentType).isEqualTo("application/octet-stream");
		assertThat(s3.objects("public").get("default/cache/b").contentDisposition).isEqualTo("inline");
		assertThat(s3.objects("private").get("default/backups/c.zip").contentDisposition).isNull();
	}

	@Test
	void testBucketRequired() {
		assertThatThrownBy(() -> new StorageImplS3(s3, "", "", tmpDir))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testCdnUrl() {
		assertThat(storage.getCdnUrl("@other", "cache", "a")).isEqualTo("https://cdn.example.com/@other/cache/a");
		assertThat(new StorageImplS3(s3, "private", "", tmpDir).getCdnUrl("", "cache", "a")).isNull();
	}

	@Test
	void testMissing() {
		assertThat(storage.exists("", "cache", "missing")).isFalse();
		assertThat(storage.size("", "cache", "missing")).isZero();
		assertThatThrownBy(() -> storage.get("", "cache", "missing")).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> storage.stream("", "cache", "missing")).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> storage.stream("", "cache", "missing", new ByteArrayOutputStream())).isInstanceOf(NotFoundException.class);
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
		assertThat(storage.get("", "cache", "a")).isEqualTo("hello".getBytes());
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
	void testStreamPropagatesServiceErrors() {
		s3.failGet = 403;

		assertThatThrownBy(() -> storage.stream("", "cache", "a", new ByteArrayOutputStream()))
			.isInstanceOf(S3Exception.class)
			.isNotInstanceOf(NotFoundException.class);
	}

	@Test
	void testMultipartUpload() throws IOException {
		storage.partSize = 4;

		storage.storeAt("", "cache", "a", new ByteArrayInputStream("0123456789".getBytes()));
		storage.storeAt("", "cache", "b", new ByteArrayInputStream("01234567".getBytes()));

		assertThat(storage.get("", "cache", "a")).isEqualTo("0123456789".getBytes());
		assertThat(storage.get("", "cache", "b")).isEqualTo("01234567".getBytes());
		assertThat(s3.objects("public").get("default/cache/a").contentDisposition).isEqualTo("inline");
		assertThat(s3.completed.get()).isEqualTo(2);
	}

	@Test
	void testFailedSourceIsAborted() {
		storage.partSize = 4;
		var failing = new InputStream() {
			int count = 0;
			@Override
			public int read() throws IOException {
				if (count++ < 10) return 'x';
				throw new IOException("source failed");
			}
		};

		assertThatThrownBy(() -> storage.storeAt("", "cache", "a", failing)).isInstanceOf(IOException.class);
		assertThat(storage.exists("", "cache", "a")).isFalse();
		assertThat(s3.aborted.get()).isEqualTo(1);
		assertThat(s3.uploads).isEmpty();
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
	void testOverwriteConflict() throws IOException {
		storage.storeAt("", "cache", "a", "one".getBytes());
		s3.changeEtagOnHead = true;

		assertThatThrownBy(() -> storage.overwrite("", "cache", "a", "x".getBytes())).isInstanceOf(ModifiedException.class);
	}

	@Test
	void testListing() throws IOException {
		storage.storeAt("", "cache", "a", "1".getBytes());
		storage.storeAt("", "cache", "b", "22".getBytes());
		storage.storeAt("", "backups", "c", "333".getBytes());
		storage.storeAt("@other", "cache", "d", "4444".getBytes());
		storage.storeAt("@private", "backups", "e", "5".getBytes());

		assertThat(storage.listTenants()).containsExactlyInAnyOrder("default", "@other", "@private");
		var origins = new ArrayList<String>();
		storage.visitTenants(origins::add);
		assertThat(origins).containsExactlyInAnyOrder("", "@other", "@private");
		assertThat(storage.listStorage("", "cache")).containsExactlyInAnyOrder(
			new Storage.StorageRef("a", 1),
			new Storage.StorageRef("b", 2));
		var ids = new ArrayList<String>();
		storage.visitStorage("@other", "cache", ids::add);
		assertThat(ids).containsExactly("d");
		assertThat(storage.listStorage("", "missing")).isEmpty();
	}

	@Test
	void testZipUsesConfiguredTmpDir() throws IOException {
		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var files = Files.list(tmpDir.resolve("s3"))) {
				assertThat(files).singleElement().satisfies(p -> assertThat(p.getFileName().toString()).startsWith("jasper-s3-"));
			}
			zipped.commit();
		}
		try (var files = Files.list(tmpDir.resolve("s3"))) {
			assertThat(files).isEmpty();
		}
		assertThat(storage.exists("", "backups", "b.zip")).isTrue();
		assertThat(s3.objects("public")).containsKey("default/backups/b.zip");
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
	void testZipClosedWithoutCommitIsDiscarded() throws IOException {
		try (var zipped = storage.zipAt("", "backups", "b.zip")) {
			try (var os = zipped.out("ref.json")) {
				os.write("[]".getBytes());
			}
		}
		assertThat(storage.exists("", "backups", "b.zip")).isFalse();
		try (var files = Files.list(tmpDir.resolve("s3"))) {
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

	static class StoredObject {
		byte[] data;
		String etag = UUID.randomUUID().toString();
		Instant lastModified = Instant.now();
		String contentType;
		String contentDisposition;
	}

	/**
	 * Minimal in-memory S3 supporting the operations and conditional writes used by {@link StorageImplS3}.
	 */
	static class FakeS3 implements S3Client {
		final Map<String, TreeMap<String, StoredObject>> buckets = new HashMap<>();
		final Map<String, TreeMap<Integer, byte[]>> uploads = new HashMap<>();
		final Map<String, StoredObject> uploadMetadata = new HashMap<>();
		final AtomicInteger completed = new AtomicInteger();
		final AtomicInteger aborted = new AtomicInteger();
		Integer failGet;
		boolean changeEtagOnHead;

		TreeMap<String, StoredObject> objects(String bucket) {
			return buckets.computeIfAbsent(bucket, b -> new TreeMap<>());
		}

		static S3Exception error(int status) {
			if (status == 404) return (S3Exception) NoSuchKeyException.builder().statusCode(404).message("Not Found").build();
			return (S3Exception) S3Exception.builder().statusCode(status).message("Error " + status).build();
		}

		StoredObject find(String bucket, String key) {
			var object = objects(bucket).get(key);
			if (object == null) throw error(404);
			return object;
		}

		void put(String bucket, String key, String ifMatch, String ifNoneMatch, StoredObject object) {
			var existing = objects(bucket).get(key);
			if ("*".equals(ifNoneMatch) && existing != null) throw error(412);
			if (ifMatch != null && (existing == null || !existing.etag.equals(ifMatch))) throw error(412);
			objects(bucket).put(key, object);
		}

		static byte[] read(RequestBody body) {
			try (var is = body.contentStreamProvider().newStream()) {
				return is.readAllBytes();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public HeadObjectResponse headObject(HeadObjectRequest r) {
			var object = find(r.bucket(), r.key());
			if (changeEtagOnHead) object.etag = UUID.randomUUID().toString();
			var etag = changeEtagOnHead ? "stale" : object.etag;
			return HeadObjectResponse.builder().contentLength((long) object.data.length).eTag(etag).build();
		}

		@Override
		public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest r) {
			if (failGet != null) throw error(failGet);
			var object = find(r.bucket(), r.key());
			return new ResponseInputStream<>(GetObjectResponse.builder().contentLength((long) object.data.length).build(),
				AbortableInputStream.create(new ByteArrayInputStream(object.data)));
		}

		@Override
		public PutObjectResponse putObject(PutObjectRequest r, RequestBody body) {
			var object = new StoredObject();
			object.data = read(body);
			object.contentType = r.contentType();
			object.contentDisposition = r.contentDisposition();
			put(r.bucket(), r.key(), r.ifMatch(), r.ifNoneMatch(), object);
			return PutObjectResponse.builder().eTag(object.etag).build();
		}

		@Override
		public DeleteObjectResponse deleteObject(DeleteObjectRequest r) {
			objects(r.bucket()).remove(r.key());
			return DeleteObjectResponse.builder().build();
		}

		@Override
		public CreateMultipartUploadResponse createMultipartUpload(CreateMultipartUploadRequest r) {
			var id = UUID.randomUUID().toString();
			uploads.put(id, new TreeMap<>());
			var metadata = new StoredObject();
			metadata.contentType = r.contentType();
			metadata.contentDisposition = r.contentDisposition();
			uploadMetadata.put(id, metadata);
			return CreateMultipartUploadResponse.builder().uploadId(id).build();
		}

		@Override
		public UploadPartResponse uploadPart(UploadPartRequest r, RequestBody body) {
			uploads.get(r.uploadId()).put(r.partNumber(), read(body));
			return UploadPartResponse.builder().eTag("part-" + r.partNumber()).build();
		}

		@Override
		public CompleteMultipartUploadResponse completeMultipartUpload(CompleteMultipartUploadRequest r) {
			var parts = uploads.remove(r.uploadId());
			var os = new ByteArrayOutputStream();
			for (var part : r.multipartUpload().parts()) os.writeBytes(parts.get(part.partNumber()));
			var object = uploadMetadata.remove(r.uploadId());
			object.data = os.toByteArray();
			put(r.bucket(), r.key(), r.ifMatch(), r.ifNoneMatch(), object);
			completed.incrementAndGet();
			return CompleteMultipartUploadResponse.builder().build();
		}

		@Override
		public AbortMultipartUploadResponse abortMultipartUpload(AbortMultipartUploadRequest r) {
			uploads.remove(r.uploadId());
			uploadMetadata.remove(r.uploadId());
			aborted.incrementAndGet();
			return AbortMultipartUploadResponse.builder().build();
		}

		@Override
		public ListObjectsV2Response listObjectsV2(ListObjectsV2Request r) {
			var prefix = r.prefix() == null ? "" : r.prefix();
			var contents = new ArrayList<S3Object>();
			var prefixes = new TreeSet<String>();
			for (var e : objects(r.bucket()).entrySet()) {
				if (!e.getKey().startsWith(prefix)) continue;
				var rest = e.getKey().substring(prefix.length());
				if (r.delimiter() != null && rest.contains(r.delimiter())) {
					prefixes.add(prefix + rest.substring(0, rest.indexOf(r.delimiter()) + 1));
				} else {
					contents.add(S3Object.builder()
						.key(e.getKey())
						.size((long) e.getValue().data.length)
						.lastModified(e.getValue().lastModified)
						.build());
				}
			}
			return ListObjectsV2Response.builder()
				.contents(contents)
				.commonPrefixes(prefixes.stream().map(p -> CommonPrefix.builder().prefix(p).build()).toList())
				.isTruncated(false)
				.build();
		}

		@Override
		public String serviceName() {
			return "s3";
		}

		@Override
		public void close() {}
	}
}
