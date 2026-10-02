package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;

/**
 * {@link Storage} backed by an S3 (or S3-compatible) bucket.
 * Created by {@link StorageRouter} for each bucket routed to the "s3" storage provider.
 * Only buckets with a CDN base URL are served by a CDN, and their objects are stored with
 * a content type and {@code Content-Disposition: inline}. Objects are keyed as {@code tenant/namespace/id}.
 */
public class StorageImplS3 implements Storage {
	private final Logger logger = LoggerFactory.getLogger(StorageImplS3.class);

	private static final int NOT_FOUND = 404;
	private static final int CONFLICT = 409;
	private static final int PRECONDITION_FAILED = 412;
	/**
	 * Multipart part size for streaming uploads. S3 requires at least 5 MiB for every part except the last.
	 */
	private static final int PART_SIZE = 8 * 1024 * 1024;

	private final S3Client s3Client;
	private final String bucket;
	private final String cdnBaseUrl;
	private final Path tmpDir;
	int partSize = PART_SIZE;

	public StorageImplS3(S3Client s3Client, String bucket, String cdnBaseUrl, Path tmpDir) {
		if (isBlank(bucket)) throw new IllegalArgumentException("S3 bucket is required");
		this.s3Client = s3Client;
		this.bucket = bucket;
		this.cdnBaseUrl = isBlank(cdnBaseUrl) ? null : stripEnd(cdnBaseUrl, "/");
		this.tmpDir = tmpDir;
	}

	record S3Location(String bucket, String key) {}

	/**
	 * Public CDN URL for an object, or null if this bucket is not served by a CDN.
	 */
	@Override
	public String getCdnUrl(String origin, String namespace, String id) {
		if (cdnBaseUrl == null) return null;
		return cdnBaseUrl + "/" + UriUtils.encodePath(key(origin, namespace, id), StandardCharsets.UTF_8);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public byte[] get(String origin, String namespace, String id) {
		var location = location(origin, namespace, id);
		try (var is = s3Client.getObject(r -> r.bucket(location.bucket()).key(location.key()))) {
			return is.readAllBytes();
		} catch (S3Exception e) {
			if (e.statusCode() == NOT_FOUND) throw new NotFoundException("Cache " + id);
			throw e;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public boolean exists(String origin, String namespace, String id) {
		return head(location(origin, namespace, id)) != null;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long size(String origin, String namespace, String id) {
		var head = head(location(origin, namespace, id));
		return head == null || head.contentLength() == null ? 0 : head.contentLength();
	}

	@Timed(value = "jasper.storage", histogram = true)
	public InputStream stream(String origin, String namespace, String id) {
		var location = location(origin, namespace, id);
		try {
			return s3Client.getObject(r -> r.bucket(location.bucket()).key(location.key()));
		} catch (S3Exception e) {
			if (e.statusCode() == NOT_FOUND) throw new NotFoundException("Storage file (" + origin + ", " + namespace + ") " + id);
			throw e;
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long stream(String origin, String namespace, String id, OutputStream os) {
		try (var is = stream(origin, namespace, id)) {
			return is.transferTo(os);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Zipped streamZip(String origin, String namespace, String id) throws IOException {
		return new ZippedS3(origin, namespace, id, false);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitTenants(PathVisitor v) {
		for (var tenant : listTenants()) v.visit(tenantOrigin(tenant));
	}

	@Override
	public List<String> listTenants() {
		var result = new ArrayList<String>();
		for (var dir : directories()) {
			result.add(dir.prefix().substring(0, dir.prefix().length() - 1));
		}
		return result;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitStorage(String origin, String namespace, PathVisitor v) {
		var prefix = prefix(origin, namespace);
		for (var object : files(prefix)) v.visit(object.key().substring(prefix.length()));
	}

	@Override
	public List<StorageRef> listStorage(String origin, String namespace) {
		var prefix = prefix(origin, namespace);
		var result = new ArrayList<StorageRef>();
		for (var object : files(prefix)) {
			result.add(new StorageRef(object.key().substring(prefix.length()), object.size() == null ? 0 : object.size()));
		}
		return result;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void overwrite(String origin, String namespace, String id, byte[] cache) throws IOException {
		var location = location(origin, namespace, id);
		var existing = head(location);
		if (existing == null) throw new NotFoundException("Cache " + id);
		try {
			s3Client.putObject(r -> metadata(r.bucket(location.bucket()).key(location.key()).ifMatch(existing.eTag()), location, id),
				RequestBody.fromBytes(cache));
		} catch (S3Exception e) {
			if (e.statusCode() == PRECONDITION_FAILED || e.statusCode() == CONFLICT) throw new ModifiedException("Cache " + id);
			throw new IOException(e);
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public String store(String origin, String namespace, byte[] cache) throws IOException {
		var id = UUID.randomUUID().toString();
		storeAt(origin, namespace, id, cache);
		return id;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void storeAt(String origin, String namespace, String id, byte[] cache) throws IOException {
		var location = location(origin, namespace, id);
		try {
			s3Client.putObject(r -> metadata(r.bucket(location.bucket()).key(location.key()).ifNoneMatch("*"), location, id),
				RequestBody.fromBytes(cache));
		} catch (S3Exception e) {
			if (e.statusCode() == PRECONDITION_FAILED || e.statusCode() == CONFLICT) throw new AlreadyExistsException();
			throw new IOException(e);
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void storeAt(String origin, String namespace, String id, InputStream is) throws IOException {
		var location = location(origin, namespace, id);
		if (head(location) != null) throw new AlreadyExistsException();
		upload(location, id, is);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public String store(String origin, String namespace, InputStream is) throws IOException {
		var id = UUID.randomUUID().toString();
		storeAt(origin, namespace, id, is);
		return id;
	}

	@Override
	public Zipped zipAt(String origin, String namespace, String id) throws IOException {
		if (exists(origin, namespace, id)) throw new AlreadyExistsException();
		return new ZippedS3(origin, namespace, id, true);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void delete(String origin, String namespace, String id) throws IOException {
		var location = location(origin, namespace, id);
		try {
			// S3 deletes are idempotent, so check first to report missing files like the local storage
			if (head(location) == null) throw new NoSuchFileException(location.key());
			s3Client.deleteObject(r -> r.bucket(location.bucket()).key(location.key()));
		} catch (S3Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public void backup(String origin, String namespace, Zipped backup, Instant modifiedAfter) throws IOException {
		var prefix = prefix(origin, namespace);
		var dir = backup.get(namespace);
		for (var object : files(prefix)) {
			if (modifiedAfter != null && (object.lastModified() == null || !object.lastModified().isAfter(modifiedAfter))) continue;
			Files.createDirectories(dir);
			try (var is = s3Client.getObject(r -> r.bucket(bucket).key(object.key()))) {
				Files.copy(is, backup.get(namespace, object.key().substring(prefix.length())));
			}
		}
	}

	@Override
	public void restore(String origin, String namespace, Zipped backup) throws IOException {
		if (!Files.exists(backup.get(namespace))) return;
		List<Path> files;
		try (var w = Files.walk(backup.get(namespace))) {
			files = w.filter(Files::isRegularFile).collect(Collectors.toList());
		}
		for (var f : files) {
			try (var is = Files.newInputStream(f)) {
				storeAt(origin, namespace, f.getFileName().toString(), is);
			} catch (AlreadyExistsException e) {
				// TODO: overwrite option?
			}
		}
	}

	String prefix(String origin, String namespace) {
		sanitize(origin, namespace);
		return originTenant(origin) + "/" + namespace + "/";
	}

	String key(String origin, String namespace, String id) {
		sanitize(origin, namespace, id);
		return originTenant(origin) + "/" + namespace + "/" + id;
	}

	S3Location location(String origin, String namespace, String id) {
		return new S3Location(bucket, key(origin, namespace, id));
	}

	private HeadObjectResponse head(S3Location location) {
		try {
			return s3Client.headObject(r -> r.bucket(location.bucket()).key(location.key()));
		} catch (S3Exception e) {
			if (e.statusCode() == NOT_FOUND) return null;
			throw e;
		}
	}

	/**
	 * Objects in a CDN bucket are served publicly, so set a content type and render inline in browsers.
	 */
	private PutObjectRequest.Builder metadata(PutObjectRequest.Builder builder, S3Location location, String id) {
		if (cdnBaseUrl != null) {
			builder.contentType(contentType(id)).contentDisposition("inline");
		}
		return builder;
	}

	private static String contentType(String id) {
		return MediaTypeFactory.getMediaType(id).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
	}

	private Iterable<CommonPrefix> directories() {
		return s3Client.listObjectsV2Paginator(r -> r.bucket(bucket).delimiter("/")).commonPrefixes();
	}

	private Iterable<S3Object> files(String prefix) {
		var objects = s3Client.listObjectsV2Paginator(r -> r.bucket(bucket).prefix(prefix).delimiter("/")).contents();
		return () -> objects.stream()
			.filter(object -> object.key().length() > prefix.length())
			.iterator();
	}

	/**
	 * Streams to S3 without knowing the content length. Small sources are sent with a single
	 * conditional put. Larger sources use a multipart upload, buffering one part at a time,
	 * so heap usage is bounded by the part size. The object only becomes visible once the
	 * upload is completed, and the write fails if it was created concurrently. If reading or
	 * writing fails the multipart upload is aborted, so no partial object is published.
	 */
	private void upload(S3Location location, String id, InputStream is) throws IOException {
		var buffer = new byte[partSize];
		var read = is.readNBytes(buffer, 0, partSize);
		try {
			if (read < partSize) {
				s3Client.putObject(r -> metadata(r.bucket(location.bucket()).key(location.key()).ifNoneMatch("*"), location, id),
					RequestBody.fromBytes(Arrays.copyOf(buffer, read)));
				return;
			}
			var uploadId = s3Client.createMultipartUpload(r -> {
				r.bucket(location.bucket()).key(location.key());
				if (cdnBaseUrl != null) r.contentType(contentType(id)).contentDisposition("inline");
			}).uploadId();
			try {
				var parts = new ArrayList<CompletedPart>();
				while (read > 0) {
					var partNumber = parts.size() + 1;
					var length = read;
					var etag = s3Client.uploadPart(r -> r.bucket(location.bucket()).key(location.key()).uploadId(uploadId).partNumber(partNumber),
						RequestBody.fromInputStream(new ByteArrayInputStream(buffer, 0, length), length)).eTag();
					parts.add(CompletedPart.builder().partNumber(partNumber).eTag(etag).build());
					read = is.readNBytes(buffer, 0, partSize);
				}
				s3Client.completeMultipartUpload(r -> r.bucket(location.bucket()).key(location.key()).uploadId(uploadId)
					.multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
					.ifNoneMatch("*"));
			} catch (IOException | RuntimeException e) {
				try {
					s3Client.abortMultipartUpload(r -> r.bucket(location.bucket()).key(location.key()).uploadId(uploadId));
				} catch (RuntimeException abort) {
					e.addSuppressed(abort);
				}
				throw e;
			}
		} catch (S3Exception e) {
			if (e.statusCode() == PRECONDITION_FAILED || e.statusCode() == CONFLICT) throw new AlreadyExistsException();
			throw new IOException(e);
		}
	}

	/**
	 * Zip archives need random access to the central directory, so the archive is
	 * spooled to a private temporary file and transferred to or from S3 by streaming.
	 */
	private class ZippedS3 implements Zipped {
		private final Path dir;
		private final FileSystem zipfs;
		private final boolean create;
		private final String origin;
		private final String id;
		private final S3Location location;
		private boolean closed;

		public ZippedS3(String origin, String namespace, String id, boolean create) throws IOException {
			this.origin = origin;
			this.id = id;
			this.create = create;
			this.location = location(origin, namespace, id);
			dir = Files.createTempDirectory(Files.createDirectories(tmpDir), "jasper-s3-");
			try {
				var file = dir.resolve("archive.zip");
				if (!create) {
					logger.debug("{} Downloading zip {}", origin, location.key());
					try (var is = s3Client.getObject(r -> r.bucket(location.bucket()).key(location.key()))) {
						Files.copy(is, file);
					} catch (S3Exception e) {
						if (e.statusCode() == NOT_FOUND) throw new NoSuchFileException(location.key());
						throw e;
					}
				}
				zipfs = FileSystems.newFileSystem(file, Map.of("create", create ? "true" : "false"));
			} catch (IOException | RuntimeException e) {
				FileSystemUtils.deleteRecursively(dir);
				throw e;
			}
		}

		@Override
		public Path get(String first, String... more) {
			return zipfs.getPath(first, more);
		}

		@Override
		public InputStream in(String filename) {
			logger.debug("{} Reading zip file {}", origin, filename);
			try {
				return Files.newInputStream(zipfs.getPath(filename));
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public OutputStream out(String filename) throws IOException {
			logger.debug("{} Zipping up {}", origin, filename);
			return Files.newOutputStream(zipfs.getPath(filename));
		}

		@Override
		public Iterator<InputStream> list(String pattern) throws IOException {
			logger.debug("{} Listing files matching pattern {}", origin, pattern);
			var root = zipfs.getPath("/");
			if (!Files.exists(root)) return Collections.emptyIterator();
			try (var stream = Files.walk(root)) {
				var files = stream
					.filter(Files::isRegularFile)
					.map(p -> p.toString().startsWith("/") ? p.toString().substring(1) : p.toString())
					.filter(name -> name.matches(pattern))
					.collect(Collectors.toList());
				return files.stream()
					.map(filename -> {
						try {
							return Files.newInputStream(zipfs.getPath(filename));
						} catch (IOException e) {
							logger.warn("{} Failed to open file {} in zip", origin, filename, e);
							return null;
						}
					})
					.filter(is -> is != null)
					.iterator();
			}
		}

		@Override
		public void commit() throws IOException {
			if (closed) throw new IOException("Zip already closed");
			closed = true;
			try {
				zipfs.close();
				if (create) {
					logger.debug("{} Uploading zip {}", origin, location.key());
					try (var is = Files.newInputStream(dir.resolve("archive.zip"))) {
						upload(location, id, is);
					}
				}
			} finally {
				FileSystemUtils.deleteRecursively(dir);
			}
		}

		@Override
		public void close() throws IOException {
			if (closed) return;
			closed = true;
			try {
				zipfs.close();
			} finally {
				FileSystemUtils.deleteRecursively(dir);
			}
		}
	}
}
