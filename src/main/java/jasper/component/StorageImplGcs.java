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
import jakarta.annotation.PostConstruct;
import jasper.config.Config.GcsRoute;
import jasper.config.Config.ServerConfig;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;

/**
 * {@link Storage} backed by Google Cloud Storage buckets.
 * Each tenant and namespace is routed to a bucket by the first matching
 * {@link GcsRoute} in the server config, or to the default bucket if no route matches.
 * A route or default with the "local" storage provider stores the tenant and namespace in
 * {@link StorageImplLocal} instead.
 * Routes are reloaded whenever the server config changes.
 * Only routes with a CDN base URL are served by a CDN, and their buckets may
 * not store any other files. Objects are keyed as {@code tenant/namespace/id}.
 */
@Primary
@Profile("storage & gcs")
@Component
public class StorageImplGcs implements Storage {
	private final Logger logger = LoggerFactory.getLogger(StorageImplGcs.class);

	private static final int NOT_FOUND = 404;
	private static final int PRECONDITION_FAILED = 412;

	private final com.google.cloud.storage.Storage gcsClient;
	private final ConfigCache configs;
	private final StorageImplLocal local;
	Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
	private volatile Routing routing;

	public StorageImplGcs(com.google.cloud.storage.Storage gcsClient, ConfigCache configs, StorageImplLocal local) {
		this.gcsClient = gcsClient;
		this.configs = configs;
		this.local = local;
	}

	@PostConstruct
	public void init() {
		configs.rootUpdate(root -> {
			try {
				update(root);
			} catch (RuntimeException e) {
				if (routing == null) {
					// Keep starting so the server config can still be fixed at runtime
					logger.error("Invalid GCS server config, storage is unavailable until it is fixed: {}", e.getMessage());
				} else {
					logger.error("Invalid GCS server config, keeping previous routes: {}", e.getMessage());
				}
			}
		});
	}

	/**
	 * Replace the bucket routes with the server config.
	 * Throws {@link IllegalArgumentException} and keeps the existing routes if the config is invalid.
	 */
	void update(ServerConfig root) {
		var gcsRoutes = root.getGcsRoutes() == null ? List.<GcsRoute>of() : root.getGcsRoutes();
		var defaultBucket = providerBucket(root.getStorage(), root.getGcsBucket());
		var cdnBuckets = new HashSet<String>();
		var privateBuckets = new HashSet<String>();
		if (defaultBucket != null) privateBuckets.add(defaultBucket);
		for (var r : gcsRoutes) {
			var bucket = providerBucket(r.getStorage(), r.getBucket());
			if (isBlank(r.getCdnBaseUrl())) {
				if (bucket != null) privateBuckets.add(bucket);
			} else {
				if (bucket == null) throw new IllegalArgumentException("GCS route with a CDN must use gcs storage");
				if (isEmpty(r.getNamespaces())) throw new IllegalArgumentException("GCS route for CDN bucket " + r.getBucket() + " must list its namespaces");
				cdnBuckets.add(r.getBucket());
			}
		}
		cdnBuckets.retainAll(privateBuckets);
		if (!cdnBuckets.isEmpty()) throw new IllegalArgumentException("GCS buckets served by a CDN may only be used by CDN routes: " + cdnBuckets);
		routing = new Routing(defaultBucket, gcsRoutes.stream().map(r -> new Route(
			providerBucket(r.getStorage(), r.getBucket()),
			r.getNamespaces() == null ? Set.of() : Set.copyOf(r.getNamespaces()),
			r.getTenants() == null ? Set.of() : r.getTenants().stream().map(t -> formatOrigin(t)).collect(Collectors.toSet()),
			isBlank(r.getCdnBaseUrl()) ? null : stripEnd(r.getCdnBaseUrl(), "/")
		)).toList());
	}

	/**
	 * GCS bucket for a storage provider, or null for local storage.
	 */
	private String providerBucket(String storage, String bucket) {
		if (isBlank(storage) || "gcs".equals(storage)) {
			if (isBlank(bucket)) throw new IllegalArgumentException("GCS bucket is required for gcs storage");
			return bucket;
		}
		if (!"local".equals(storage)) throw new IllegalArgumentException("Unknown storage provider " + storage);
		if (!isBlank(bucket)) throw new IllegalArgumentException("Local storage does not use a GCS bucket");
		return null;
	}

	private Routing routing() {
		var r = routing;
		if (r == null) throw new IllegalStateException("GCS bucket is not configured in the server config");
		return r;
	}

	/**
	 * Public CDN URL for an object, or null if its tenant and namespace are not routed to a CDN.
	 */
	@Override
	public String getCdnUrl(String origin, String namespace, String id) {
		var routing = this.routing;
		if (routing == null) return null;
		var route = route(routing, origin, namespace);
		if (route == null || route.cdnBaseUrl() == null) return null;
		return route.cdnBaseUrl() + "/" + UriUtils.encodePath(key(origin, namespace, id), StandardCharsets.UTF_8);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public byte[] get(String origin, String namespace, String id) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.get(origin, namespace, id);
		try {
			return gcsClient.readAllBytes(blobId(bucket, origin, namespace, id));
		} catch (StorageException e) {
			if (e.getCode() == NOT_FOUND) throw new NotFoundException("Cache " + id);
			throw e;
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public boolean exists(String origin, String namespace, String id) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.exists(origin, namespace, id);
		return exists(blobId(bucket, origin, namespace, id));
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long size(String origin, String namespace, String id) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.size(origin, namespace, id);
		var blob = gcsClient.get(blobId(bucket, origin, namespace, id), BlobGetOption.fields(BlobField.SIZE));
		return blob == null || blob.getSize() == null ? 0 : blob.getSize();
	}

	@Timed(value = "jasper.storage", histogram = true)
	public InputStream stream(String origin, String namespace, String id) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.stream(origin, namespace, id);
		return Channels.newInputStream(blob(blobId(bucket, origin, namespace, id)).reader());
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long stream(String origin, String namespace, String id, OutputStream os) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.stream(origin, namespace, id, os);
		try (var is = Channels.newInputStream(blob(blobId(bucket, origin, namespace, id)).reader())) {
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
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.streamZip(origin, namespace, id);
		return new ZippedGcs(origin, blobId(bucket, origin, namespace, id), false);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitTenants(PathVisitor v) {
		for (var tenant : listTenants()) v.visit(tenantOrigin(tenant));
	}

	@Override
	public List<String> listTenants() {
		var result = new LinkedHashSet<String>();
		var routing = routing();
		var buckets = new LinkedHashSet<String>();
		buckets.add(routing.bucket());
		for (var r : routing.routes()) buckets.add(r.bucket());
		if (buckets.remove(null)) result.addAll(local.listTenants());
		for (var bucket : buckets) {
			for (var blob : gcsClient.list(bucket, BlobListOption.prefix(""), BlobListOption.currentDirectory()).iterateAll()) {
				if (!blob.isDirectory()) continue;
				result.add(childName(blob, ""));
			}
		}
		return new ArrayList<>(result);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitStorage(String origin, String namespace, PathVisitor v) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) { local.visitStorage(origin, namespace, v); return; }
		var prefix = prefix(origin, namespace);
		for (var blob : files(bucket, prefix)) v.visit(childName(blob, prefix));
	}

	@Override
	public List<StorageRef> listStorage(String origin, String namespace) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.listStorage(origin, namespace);
		var prefix = prefix(origin, namespace);
		var result = new ArrayList<StorageRef>();
		for (var blob : files(bucket, prefix)) {
			result.add(new StorageRef(childName(blob, prefix), blob.getSize() == null ? 0 : blob.getSize()));
		}
		return result;
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void overwrite(String origin, String namespace, String id, byte[] cache) throws IOException {
		var bucket = bucket(origin, namespace);
		if (bucket == null) { local.overwrite(origin, namespace, id, cache); return; }
		var existing = gcsClient.get(blobId(bucket, origin, namespace, id), BlobGetOption.fields(BlobField.GENERATION));
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
		var bucket = bucket(origin, namespace);
		if (bucket == null) { local.storeAt(origin, namespace, id, cache); return; }
		try {
			gcsClient.create(BlobInfo.newBuilder(blobId(bucket, origin, namespace, id)).build(), cache, BlobTargetOption.doesNotExist());
		} catch (StorageException e) {
			if (e.getCode() == PRECONDITION_FAILED) throw new AlreadyExistsException();
			throw new IOException(e);
		}
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void storeAt(String origin, String namespace, String id, InputStream is) throws IOException {
		var bucket = bucket(origin, namespace);
		if (bucket == null) { local.storeAt(origin, namespace, id, is); return; }
		storeAt(blobId(bucket, origin, namespace, id), is);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public String store(String origin, String namespace, InputStream is) throws IOException {
		var id = UUID.randomUUID().toString();
		storeAt(origin, namespace, id, is);
		return id;
	}

	@Override
	public Zipped zipAt(String origin, String namespace, String id) throws IOException {
		var bucket = bucket(origin, namespace);
		if (bucket == null) return local.zipAt(origin, namespace, id);
		var blobId = blobId(bucket, origin, namespace, id);
		if (exists(blobId)) throw new AlreadyExistsException();
		return new ZippedGcs(origin, blobId, true);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void delete(String origin, String namespace, String id) throws IOException {
		var bucket = bucket(origin, namespace);
		if (bucket == null) { local.delete(origin, namespace, id); return; }
		var blobId = blobId(bucket, origin, namespace, id);
		try {
			if (!gcsClient.delete(blobId)) throw new NoSuchFileException(blobId.getName());
		} catch (StorageException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void backup(String origin, String namespace, Zipped backup, Instant modifiedAfter) throws IOException {
		var bucket = bucket(origin, namespace);
		if (bucket == null) { local.backup(origin, namespace, backup, modifiedAfter); return; }
		var prefix = prefix(origin, namespace);
		var dir = backup.get(namespace);
		for (var blob : files(bucket, prefix)) {
			if (modifiedAfter != null && (blob.getUpdateTimeOffsetDateTime() == null || !blob.getUpdateTimeOffsetDateTime().toInstant().isAfter(modifiedAfter))) continue;
			Files.createDirectories(dir);
			try (var is = Channels.newInputStream(blob.reader())) {
				Files.copy(is, backup.get(namespace, childName(blob, prefix)));
			}
		}
	}

	@Override
	public void restore(String origin, String namespace, Zipped backup) throws IOException {
		var bucket = bucket(origin, namespace);
		if (bucket == null) { local.restore(origin, namespace, backup); return; }
		if (!Files.exists(backup.get(namespace))) return;
		List<Path> files;
		try (var w = Files.walk(backup.get(namespace))) {
			files = w.filter(Files::isRegularFile).collect(Collectors.toList());
		}
		for (var f : files) {
			try (var is = Files.newInputStream(f)) {
				storeAt(blobId(bucket, origin, namespace, f.getFileName().toString()), is);
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

	BlobId blobId(String origin, String namespace, String id) {
		var bucket = bucket(origin, namespace);
		if (bucket == null) throw new IllegalStateException("Tenant and namespace are stored locally");
		return blobId(bucket, origin, namespace, id);
	}

	private BlobId blobId(String bucket, String origin, String namespace, String id) {
		return BlobId.of(bucket, key(origin, namespace, id));
	}

	private boolean exists(BlobId blobId) {
		return gcsClient.get(blobId, BlobGetOption.fields(BlobField.NAME)) != null;
	}

	private void storeAt(BlobId blobId, InputStream is) throws IOException {
		if (exists(blobId)) throw new AlreadyExistsException();
		upload(blobId, is);
	}

	private Blob blob(BlobId blobId) {
		var blob = gcsClient.get(blobId);
		if (blob == null) throw new NotFoundException("Storage file " + blobId.getName());
		return blob;
	}

	private Route route(Routing routing, String origin, String namespace) {
		sanitize(origin, namespace);
		var tenant = originTenant(origin);
		for (var r : routing.routes()) {
			if (r.matches(tenant, namespace)) return r;
		}
		return null;
	}

	/**
	 * GCS bucket for a tenant and namespace, or null if they are stored locally.
	 * Resolved once per operation so a concurrent route update can't split it across buckets.
	 */
	private String bucket(String origin, String namespace) {
		var routing = routing();
		var route = route(routing, origin, namespace);
		return route == null ? routing.bucket() : route.bucket();
	}

	private Iterable<Blob> files(String bucket, String prefix) {
		return () -> StreamSupport.stream(gcsClient.list(bucket, BlobListOption.prefix(prefix), BlobListOption.currentDirectory()).iterateAll().spliterator(), false)
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

	private record Routing(String bucket, List<Route> routes) {}

	private record Route(String bucket, Set<String> namespaces, Set<String> tenants, String cdnBaseUrl) {
		boolean matches(String tenant, String namespace) {
			return (namespaces.isEmpty() || namespaces.contains(namespace))
				&& (tenants.isEmpty() || tenants.contains(tenant));
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

		public ZippedGcs(String origin, BlobId blobId, boolean create) throws IOException {
			this.origin = origin;
			this.create = create;
			this.blobId = blobId;
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
		public void commit() throws IOException {
			if (closed) throw new IOException("Zip already closed");
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
