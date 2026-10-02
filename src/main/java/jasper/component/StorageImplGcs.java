package jasper.component;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage.BlobField;
import com.google.cloud.storage.Storage.BlobGetOption;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.Storage.BlobWriteOption;
import com.google.cloud.storage.StorageException;
import io.micrometer.core.annotation.Timed;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * {@link Storage} backed by a single Google Cloud Storage bucket.
 * Objects are keyed as {@code tenant/namespace/id}.
 */
@Profile("gcs")
@Component
public class StorageImplGcs implements Storage {
	private final Logger logger = LoggerFactory.getLogger(StorageImplGcs.class);

	private static final int NOT_FOUND = 404;
	private static final int PRECONDITION_FAILED = 412;

	private final com.google.cloud.storage.Storage gcsClient;
	private final String bucketName;
	private final Path tmpDir;

	public StorageImplGcs(
		com.google.cloud.storage.Storage gcsClient,
		@Value("${application.storage.gcs.bucket-name}") String bucketName,
		@Value("${application.storage.gcs.tmp-dir:${java.io.tmpdir}}") Path tmpDir
	) {
		this.gcsClient = gcsClient;
		this.bucketName = bucketName;
		this.tmpDir = tmpDir;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public byte[] get(String origin, String namespace, String id) {
		try {
			return gcsClient.readAllBytes(blobId(origin, namespace, id));
		} catch (StorageException e) {
			if (e.getCode() == NOT_FOUND) throw new NotFoundException("Cache " + id);
			throw e;
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public boolean exists(String origin, String namespace, String id) {
		return gcsClient.get(blobId(origin, namespace, id), BlobGetOption.fields(BlobField.NAME)) != null;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long size(String origin, String namespace, String id) {
		var blob = gcsClient.get(blobId(origin, namespace, id), BlobGetOption.fields(BlobField.SIZE));
		return blob == null || blob.getSize() == null ? 0 : blob.getSize();
	}

	@Timed(value = "jasper.storage", histogram = true)
	public InputStream stream(String origin, String namespace, String id) {
		return Channels.newInputStream(blob(origin, namespace, id).reader());
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long stream(String origin, String namespace, String id, OutputStream os) {
		try (var is = stream(origin, namespace, id)) {
			return is.transferTo(os);
		} catch (StorageException e) {
			if (e.getCode() == NOT_FOUND) throw new NotFoundException("Storage file (" + origin + ", " + namespace + ") " + id);
			throw e;
		} catch (IOException e) {
			if (e.getCause() instanceof StorageException se && se.getCode() == NOT_FOUND) throw new NotFoundException("Storage file (" + origin + ", " + namespace + ") " + id);
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Zipped streamZip(String origin, String namespace, String id) throws IOException {
		return new ZippedGcs(origin, namespace, id, false);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitTenants(PathVisitor v) {
		for (var tenant : listTenants()) v.visit(tenantOrigin(tenant));
	}

	@Override
	public List<String> listTenants() {
		var result = new ArrayList<String>();
		for (var blob : gcsClient.list(bucketName, BlobListOption.prefix(""), BlobListOption.currentDirectory()).iterateAll()) {
			if (!blob.isDirectory()) continue;
			result.add(childName(blob, ""));
		}
		return result;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitStorage(String origin, String namespace, PathVisitor v) {
		var prefix = prefix(origin, namespace);
		for (var blob : files(prefix)) v.visit(childName(blob, prefix));
	}

	@Override
	public List<StorageRef> listStorage(String origin, String namespace) {
		var prefix = prefix(origin, namespace);
		var result = new ArrayList<StorageRef>();
		for (var blob : files(prefix)) {
			result.add(new StorageRef(childName(blob, prefix), blob.getSize() == null ? 0 : blob.getSize()));
		}
		return result;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void overwrite(String origin, String namespace, String id, byte[] cache) throws IOException {
		var existing = gcsClient.get(blobId(origin, namespace, id), BlobGetOption.fields(BlobField.GENERATION));
		if (existing == null) throw new NotFoundException("Cache " + id);
		// Real GCS always returns a generation; only the LocalStorageHelper test fake omits it
		if (existing.getGeneration() == null) logger.warn("{} No generation for {}, overwriting without concurrency check", origin, existing.getName());
		try {
			gcsClient.create(BlobInfo.newBuilder(existing.getBlobId()).build(), cache, existing.getGeneration() == null
				? new BlobTargetOption[0]
				: new BlobTargetOption[]{ BlobTargetOption.generationMatch(existing.getGeneration()) });
		} catch (StorageException e) {
			if (e.getCode() == PRECONDITION_FAILED) throw new ModifiedException("Cache " + id);
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
		try {
			gcsClient.create(BlobInfo.newBuilder(blobId(origin, namespace, id)).build(), cache, BlobTargetOption.doesNotExist());
		} catch (StorageException e) {
			if (e.getCode() == PRECONDITION_FAILED) throw new AlreadyExistsException();
			throw new IOException(e);
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void storeAt(String origin, String namespace, String id, InputStream is) throws IOException {
		if (exists(origin, namespace, id)) throw new AlreadyExistsException();
		upload(blobId(origin, namespace, id), is);
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
		return new ZippedGcs(origin, namespace, id, true);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void delete(String origin, String namespace, String id) throws IOException {
		var blobId = blobId(origin, namespace, id);
		try {
			if (!gcsClient.delete(blobId)) throw new NoSuchFileException(blobId.getName());
		} catch (StorageException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void backup(String origin, String namespace, Zipped backup, Instant modifiedAfter) throws IOException {
		var prefix = prefix(origin, namespace);
		var dir = backup.get(namespace);
		for (var blob : files(prefix)) {
			if (modifiedAfter != null && (blob.getUpdateTimeOffsetDateTime() == null || !blob.getUpdateTimeOffsetDateTime().toInstant().isAfter(modifiedAfter))) continue;
			Files.createDirectories(dir);
			try (var is = Channels.newInputStream(blob.reader())) {
				Files.copy(is, backup.get(namespace, childName(blob, prefix)));
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

	BlobId blobId(String origin, String namespace, String id) {
		sanitize(origin, namespace, id);
		return BlobId.of(bucketName, originTenant(origin) + "/" + namespace + "/" + id);
	}

	private Blob blob(String origin, String namespace, String id) {
		var blob = gcsClient.get(blobId(origin, namespace, id));
		if (blob == null) throw new NotFoundException("Storage file (" + origin + ", " + namespace + ") " + id);
		return blob;
	}

	private Iterable<Blob> files(String prefix) {
		return () -> StreamSupport.stream(gcsClient.list(bucketName, BlobListOption.prefix(prefix), BlobListOption.currentDirectory()).iterateAll().spliterator(), false)
			.filter(blob -> !blob.isDirectory() && blob.getName().length() > prefix.length())
			.iterator();
	}

	private static String childName(Blob blob, String prefix) {
		var name = blob.getName().substring(prefix.length());
		return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
	}

	/**
	 * Streams to GCS with a resumable upload. The object only becomes visible
	 * once the upload is finalized, and the write fails if it was created concurrently.
	 * The channel is only closed (finalized) after the source has been read completely.
	 * If reading or writing fails the resumable session is abandoned without being
	 * finalized, so no partial object is published. GCS discards the session after it expires.
	 */
	private void upload(BlobId blobId, InputStream is) throws IOException {
		try {
			var os = Channels.newOutputStream(gcsClient.writer(BlobInfo.newBuilder(blobId).build(), BlobWriteOption.doesNotExist()));
			is.transferTo(os);
			os.close();
		} catch (StorageException e) {
			if (e.getCode() == PRECONDITION_FAILED) throw new AlreadyExistsException();
			throw new IOException(e);
		} catch (IOException e) {
			if (e.getCause() instanceof StorageException se && se.getCode() == PRECONDITION_FAILED) throw new AlreadyExistsException();
			throw e;
		}
	}

	/**
	 * Zip archives need random access to the central directory, so the archive is
	 * spooled to a private temporary file and transferred to or from GCS by streaming.
	 */
	private class ZippedGcs implements Zipped {
		private final Path dir;
		private final FileSystem zipfs;
		private final boolean create;
		private final String origin;
		private final BlobId blobId;
		private boolean closed;

		public ZippedGcs(String origin, String namespace, String id, boolean create) throws IOException {
			this.origin = origin;
			this.create = create;
			this.blobId = blobId(origin, namespace, id);
			dir = Files.createTempDirectory(Files.createDirectories(tmpDir), "jasper-gcs-");
			try {
				var file = dir.resolve("archive.zip");
				if (!create) {
					var blob = gcsClient.get(blobId);
					if (blob == null) throw new NoSuchFileException(blobId.getName());
					logger.debug("{} Downloading zip {}", origin, blobId.getName());
					try (var is = Channels.newInputStream(blob.reader())) {
						Files.copy(is, file);
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
		public void close() throws IOException {
			if (closed) return;
			closed = true;
			try {
				zipfs.close();
				if (create) {
					logger.debug("{} Uploading zip {}", origin, blobId.getName());
					try (var is = Files.newInputStream(dir.resolve("archive.zip"))) {
						upload(blobId, is);
					}
				}
			} finally {
				FileSystemUtils.deleteRecursively(dir);
			}
		}
	}
}
