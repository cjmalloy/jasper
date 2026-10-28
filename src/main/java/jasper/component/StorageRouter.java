package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
import jasper.config.Config.ServerConfig;
import jasper.config.Config.StorageRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * {@link Storage} that routes each tenant and namespace to a storage provider.
 * The first matching {@link StorageRoute} in the server config is used, or the default
 * storage provider and bucket if no route matches. Supported storage providers are
 * "local" ({@link StorageImplLocal}), "gcs" ({@link StorageImplGcs}, requires the gcs profile)
 * and "s3" ({@link StorageImplS3}, requires the s3 profile), which may all be used at the same time.
 * Routes are reloaded whenever the server config changes.
 * Only routes with a CDN base URL are served by a CDN, and their buckets may
 * not store any other files.
 */
@Primary
@Profile("storage & (gcs | s3)")
@Component
public class StorageRouter implements Storage {
	private final Logger logger = LoggerFactory.getLogger(StorageRouter.class);

	private final ConfigCache configs;
	private final StorageImplLocal local;
	private final ObjectProvider<com.google.cloud.storage.Storage> gcsClient;
	private final ObjectProvider<S3Client> s3Client;
	private final Path tmpDir;
	private volatile Routing routing;

	public StorageRouter(
		ConfigCache configs,
		StorageImplLocal local,
		ObjectProvider<com.google.cloud.storage.Storage> gcsClient,
		ObjectProvider<S3Client> s3Client,
		@Value("${application.storage.tmp-dir:${java.io.tmpdir}}") Path tmpDir
	) {
		this.configs = configs;
		this.local = local;
		this.gcsClient = gcsClient;
		this.s3Client = s3Client;
		this.tmpDir = tmpDir;
	}

	@PostConstruct
	public void init() {
		configs.rootUpdate(root -> {
			try {
				update(root);
			} catch (RuntimeException e) {
				if (routing == null) {
					// Keep starting so the server config can still be fixed at runtime
					logger.error("Invalid storage server config, storage is unavailable until it is fixed: {}", e.getMessage());
				} else {
					logger.error("Invalid storage server config, keeping previous routes: {}", e.getMessage());
				}
			}
		});
	}

	/**
	 * Replace the storage routes with the server config.
	 * Throws {@link IllegalArgumentException} and keeps the existing routes if the config is invalid.
	 */
	void update(ServerConfig root) {
		var defaultProvider = isBlank(root.getStorage()) ? "local" : root.getStorage();
		var storageRoutes = root.getStorageRoutes() == null ? List.<StorageRoute>of() : root.getStorageRoutes();
		var providers = new HashMap<String, Storage>();
		var cdnBuckets = new HashSet<String>();
		var privateBuckets = new HashSet<String>();
		var defaultStorage = provider(providers, defaultProvider, root.getStorageBucket(), null);
		if (defaultStorage != local) privateBuckets.add(defaultProvider + ":" + root.getStorageBucket());
		var routes = new ArrayList<Route>();
		for (var r : storageRoutes) {
			var provider = isBlank(r.getStorage()) ? defaultProvider : r.getStorage();
			var cdn = !isBlank(r.getCdnBaseUrl());
			var storage = provider(providers, provider, r.getBucket(), cdn ? r.getCdnBaseUrl() : null);
			if (cdn) {
				if (isEmpty(r.getNamespaces())) throw new IllegalArgumentException("Storage route for CDN bucket " + r.getBucket() + " must list its namespaces");
				cdnBuckets.add(provider + ":" + r.getBucket());
			} else if (storage != local) {
				privateBuckets.add(provider + ":" + r.getBucket());
			}
			routes.add(new Route(
				r.getNamespaces() == null ? Set.of() : Set.copyOf(r.getNamespaces()),
				r.getTenants() == null ? Set.of() : r.getTenants().stream().map(t -> formatOrigin(t)).collect(Collectors.toSet()),
				storage));
		}
		cdnBuckets.retainAll(privateBuckets);
		if (!cdnBuckets.isEmpty()) throw new IllegalArgumentException("Buckets served by a CDN may only be used by CDN routes: " + cdnBuckets);
		routing = new Routing(defaultStorage, List.copyOf(routes), List.copyOf(new LinkedHashSet<>(providers.values())));
	}

	/**
	 * Storage for a provider and bucket. Reuses the storage already created for the same
	 * provider, bucket and CDN in this config.
	 */
	private Storage provider(HashMap<String, Storage> providers, String provider, String bucket, String cdnBaseUrl) {
		if ("local".equals(provider)) {
			if (!isBlank(bucket)) throw new IllegalArgumentException("Local storage does not use a bucket");
			if (cdnBaseUrl != null) throw new IllegalArgumentException("Storage route with a CDN must use a bucket");
			providers.put("local", local);
			return local;
		}
		if (!"gcs".equals(provider) && !"s3".equals(provider)) throw new IllegalArgumentException("Unknown storage provider " + provider);
		if (isBlank(bucket)) throw new IllegalArgumentException("Bucket is required for " + provider + " storage");
		return providers.computeIfAbsent(provider + ":" + bucket + ":" + cdnBaseUrl, k -> switch (provider) {
			case "gcs" -> new StorageImplGcs(client(gcsClient, "gcs"), bucket, cdnBaseUrl, tmpDir);
			default -> new StorageImplS3(client(s3Client, "s3"), bucket, cdnBaseUrl, tmpDir);
		});
	}

	private static <T> T client(ObjectProvider<T> client, String profile) {
		var c = client.getIfAvailable();
		if (c == null) throw new IllegalArgumentException("Storage provider " + profile + " requires the " + profile + " profile");
		return c;
	}

	private Routing routing() {
		var r = routing;
		if (r == null) throw new IllegalStateException("Storage is not configured in the server config");
		return r;
	}

	/**
	 * Storage for a tenant and namespace.
	 * Resolved once per operation so a concurrent route update can't split it across storages.
	 */
	Storage storage(String origin, String namespace) {
		return storage(routing(), origin, namespace);
	}

	private Storage storage(Routing routing, String origin, String namespace) {
		sanitize(origin, namespace);
		var tenant = originTenant(origin);
		for (var r : routing.routes()) {
			if (r.matches(tenant, namespace)) return r.storage();
		}
		return routing.storage();
	}

	/**
	 * Public CDN URL for an object, or null if its tenant and namespace are not routed to a CDN.
	 */
	@Override
	public String getCdnUrl(String origin, String namespace, String id) {
		var routing = this.routing;
		if (routing == null) return null;
		return storage(routing, origin, namespace).getCdnUrl(origin, namespace, id);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public byte[] get(String origin, String namespace, String id) {
		return storage(origin, namespace).get(origin, namespace, id);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public boolean exists(String origin, String namespace, String id) {
		return storage(origin, namespace).exists(origin, namespace, id);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long size(String origin, String namespace, String id) {
		return storage(origin, namespace).size(origin, namespace, id);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public InputStream stream(String origin, String namespace, String id) {
		return storage(origin, namespace).stream(origin, namespace, id);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public long stream(String origin, String namespace, String id, OutputStream os) {
		return storage(origin, namespace).stream(origin, namespace, id, os);
	}

	@Override
	public Zipped streamZip(String origin, String namespace, String id) throws IOException {
		return storage(origin, namespace).streamZip(origin, namespace, id);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitTenants(PathVisitor v) {
		for (var tenant : listTenants()) v.visit(tenantOrigin(tenant));
	}

	@Override
	public List<String> listTenants() {
		var result = new LinkedHashSet<String>();
		for (var storage : routing().storages()) result.addAll(storage.listTenants());
		return new ArrayList<>(result);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void visitStorage(String origin, String namespace, PathVisitor v) {
		storage(origin, namespace).visitStorage(origin, namespace, v);
	}

	@Override
	public List<StorageRef> listStorage(String origin, String namespace) {
		return storage(origin, namespace).listStorage(origin, namespace);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void overwrite(String origin, String namespace, String id, byte[] cache) throws IOException {
		storage(origin, namespace).overwrite(origin, namespace, id, cache);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public String store(String origin, String namespace, byte[] cache) throws IOException {
		return storage(origin, namespace).store(origin, namespace, cache);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void storeAt(String origin, String namespace, String id, byte[] cache) throws IOException {
		storage(origin, namespace).storeAt(origin, namespace, id, cache);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void storeAt(String origin, String namespace, String id, InputStream is) throws IOException {
		storage(origin, namespace).storeAt(origin, namespace, id, is);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public String store(String origin, String namespace, InputStream is) throws IOException {
		return storage(origin, namespace).store(origin, namespace, is);
	}

	@Override
	public Zipped zipAt(String origin, String namespace, String id) throws IOException {
		return storage(origin, namespace).zipAt(origin, namespace, id);
	}

	@Timed(value = "jasper.storage", histogram = true)
	public void delete(String origin, String namespace, String id) throws IOException {
		storage(origin, namespace).delete(origin, namespace, id);
	}

	@Override
	public void backup(String origin, String namespace, Zipped backup, Instant modifiedAfter) throws IOException {
		storage(origin, namespace).backup(origin, namespace, backup, modifiedAfter);
	}

	@Override
	public void restore(String origin, String namespace, Zipped backup) throws IOException {
		storage(origin, namespace).restore(origin, namespace, backup);
	}

	private record Routing(Storage storage, List<Route> routes, List<Storage> storages) {}

	private record Route(Set<String> namespaces, Set<String> tenants, Storage storage) {
		boolean matches(String tenant, String namespace) {
			return (namespaces.isEmpty() || namespaces.contains(namespace))
				&& (tenants.isEmpty() || tenants.contains(tenant));
		}
	}
}
