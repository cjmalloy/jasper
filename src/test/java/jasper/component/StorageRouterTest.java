package jasper.component;

import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import jasper.component.StorageImplS3Test.FakeS3;
import jasper.config.Config.ServerConfig;
import jasper.config.Config.StorageRoute;
import jasper.config.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StorageRouterTest {

	com.google.cloud.storage.Storage gcs;
	FakeS3 s3;
	StorageImplLocal local;

	@TempDir
	Path tmpDir;

	@BeforeEach
	void init() {
		gcs = LocalStorageHelper.customOptions(false).getService();
		s3 = new FakeS3();
		local = new StorageImplLocal();
		local.props = new Props();
		local.props.setStorage(tmpDir.resolve("local").toString());
	}

	StorageRouter router(ConfigCache configs, com.google.cloud.storage.Storage gcs, S3Client s3) {
		return new StorageRouter(configs, local, provider(gcs), provider(s3), tmpDir);
	}

	StorageRouter router(StorageRoute... routes) {
		var router = router(mock(ConfigCache.class), gcs, s3);
		router.update(root(routes));
		return router;
	}

	@SuppressWarnings("unchecked")
	static <T> ObjectProvider<T> provider(T value) {
		var provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(value);
		return provider;
	}

	static ServerConfig root(StorageRoute... routes) {
		return ServerConfig.builder()
			.storage("gcs")
			.storageBucket("private")
			.storageRoutes(List.of(routes))
			.build();
	}

	static StorageRoute route(String bucket, List<String> namespaces, List<String> tenants, String cdnBaseUrl) {
		var route = new StorageRoute();
		route.setBucket(bucket);
		route.setNamespaces(namespaces);
		route.setTenants(tenants);
		route.setCdnBaseUrl(cdnBaseUrl);
		return route;
	}

	static StorageRoute route(String storage, String bucket, List<String> namespaces, List<String> tenants, String cdnBaseUrl) {
		var route = route(bucket, namespaces, tenants, cdnBaseUrl);
		route.setStorage(storage);
		return route;
	}

	/**
	 * Provider and bucket a tenant and namespace are routed to.
	 */
	static String bucket(StorageRouter router, String origin, String namespace) {
		var storage = router.storage(origin, namespace);
		if (storage instanceof StorageImplGcs g) return "gcs:" + g.blobId(origin, namespace, "id").getBucket();
		if (storage instanceof StorageImplS3 s) return "s3:" + s.location(origin, namespace, "id").bucket();
		if (storage instanceof StorageImplLocal) return "local";
		throw new IllegalStateException();
	}

	@Test
	void testBucketRouting() throws IOException {
		var router = router(route("public", List.of("cache"), List.of(), "https://cdn.example.com/"));
		router.storeAt("", "cache", "a", "public".getBytes());
		router.storeAt("", "backups", "b", "private".getBytes());

		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");
		assertThat(bucket(router, "", "backups")).isEqualTo("gcs:private");
		assertThat(bucket(router, "", "preload")).isEqualTo("gcs:private");
		assertThat(bucket(router, "", "secrets")).isEqualTo("gcs:private");
		assertThat(bucket(router, "", "config")).isEqualTo("gcs:private");
		assertThat(router.exists("", "backups", "b")).isTrue();
		assertThat(router.listStorage("", "cache")).containsExactly(new Storage.StorageRef("a", 6));
		assertThat(router.listStorage("", "backups")).containsExactly(new Storage.StorageRef("b", 7));
		assertThat(router.getCdnUrl("@other", "cache", "a")).isEqualTo("https://cdn.example.com/@other/cache/a");
		assertThat(router.getCdnUrl("", "backups", "b.zip")).isNull();
		assertThat(router.getCdnUrl("", "secrets", "host_key")).isNull();
	}

	@Test
	void testTenantRouting() {
		var router = router(
			route("tenant-public", List.of("cache"), List.of("@tenant"), "https://tenant.example.com"),
			route("tenant-private", List.of(), List.of("@tenant"), ""),
			route("public", List.of("cache"), List.of("default"), "https://cdn.example.com"),
			route("archive", List.of("backups"), List.of(), ""));

		assertThat(bucket(router, "@tenant", "cache")).isEqualTo("gcs:tenant-public");
		assertThat(bucket(router, "@tenant", "backups")).isEqualTo("gcs:tenant-private");
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");
		assertThat(bucket(router, "", "backups")).isEqualTo("gcs:archive");
		assertThat(bucket(router, "@other", "cache")).isEqualTo("gcs:private");
		assertThat(bucket(router, "@other", "backups")).isEqualTo("gcs:archive");
		assertThat(router.getCdnUrl("@tenant", "cache", "a")).isEqualTo("https://tenant.example.com/@tenant/cache/a");
		assertThat(router.getCdnUrl("@other", "cache", "a")).isNull();
	}

	@Test
	void testMultipleProviders() throws IOException {
		var router = router(mock(ConfigCache.class), gcs, s3);
		router.update(ServerConfig.builder()
			.storage("s3")
			.storageBucket("private")
			.storageRoutes(List.of(
				route("gcs", "public", List.of("cache"), List.of(), "https://cdn.example.com"),
				route("local", "", List.of("backups"), List.of("@local"), ""),
				route("s3", "tenant", List.of(), List.of("@tenant"), "")))
			.build());

		router.storeAt("", "cache", "a", "gcs".getBytes());
		router.storeAt("", "backups", "b", "s3".getBytes());
		router.storeAt("@local", "backups", "c", "local".getBytes());
		router.storeAt("@tenant", "backups", "d", "tenant".getBytes());

		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");
		assertThat(bucket(router, "", "backups")).isEqualTo("s3:private");
		assertThat(bucket(router, "@local", "backups")).isEqualTo("local");
		assertThat(bucket(router, "@local", "secrets")).isEqualTo("s3:private");
		assertThat(bucket(router, "@tenant", "backups")).isEqualTo("s3:tenant");
		assertThat(router.get("", "cache", "a")).isEqualTo("gcs".getBytes());
		assertThat(router.get("", "backups", "b")).isEqualTo("s3".getBytes());
		assertThat(local.get("@local", "backups", "c")).isEqualTo("local".getBytes());
		assertThat(s3.objects("private")).containsOnlyKeys("default/backups/b");
		assertThat(s3.objects("tenant")).containsOnlyKeys("@tenant/backups/d");
		assertThat(router.getCdnUrl("", "cache", "a")).isEqualTo("https://cdn.example.com/default/cache/a");
		assertThat(router.getCdnUrl("", "backups", "b")).isNull();
		assertThat(router.listTenants()).containsExactlyInAnyOrder("default", "@local", "@tenant");

		try (var zipped = router.zipAt("", "backups", "b.zip")) {
			router.backup("", "cache", zipped, null);
			zipped.commit();
		}
		assertThat(s3.objects("private")).containsKey("default/backups/b.zip");
		try (var zipped = router.streamZip("", "backups", "b.zip")) {
			router.restore("@local", "cache", zipped);
		}
		assertThat(router.get("@local", "cache", "a")).isEqualTo("gcs".getBytes());
	}

	@Test
	void testRouteDefaultsToServerProvider() {
		var router = router(mock(ConfigCache.class), gcs, s3);
		router.update(root(route("public", List.of("cache"), List.of(), "")).withStorage("s3"));
		assertThat(bucket(router, "", "cache")).isEqualTo("s3:public");
		assertThat(bucket(router, "", "backups")).isEqualTo("s3:private");
	}

	@Test
	void testProviderRequiresProfile() {
		var router = router(mock(ConfigCache.class), gcs, null);
		assertThatThrownBy(() -> router.update(root().withStorage("s3"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.update(root(route("s3", "public", List.of("cache"), List.of(), ""))))
			.isInstanceOf(IllegalArgumentException.class);
		router.update(root());
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:private");
	}

	@Test
	void testCdnBucketIsolation() {
		var router = router();
		assertThatThrownBy(() -> router.update(root(route("private", List.of("cache"), List.of(), "https://cdn.example.com"))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.update(root(
			route("public", List.of("cache"), List.of(), "https://cdn.example.com"),
			route("public", List.of("backups"), List.of(), ""))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.update(root(route("public", List.of(), List.of(), "https://cdn.example.com"))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.update(root().withStorageBucket(""))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.update(root().withStorage("unknown"))).isInstanceOf(IllegalArgumentException.class);
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:private");
		// Buckets with the same name in different providers are different buckets
		router.update(root(
			route("public", List.of("cache"), List.of(), "https://cdn.example.com"),
			route("s3", "public", List.of("backups"), List.of(), "")));
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");
		assertThat(bucket(router, "", "backups")).isEqualTo("s3:public");
	}

	@Test
	void testLocalRouting() throws IOException {
		var router = router(mock(ConfigCache.class), gcs, s3);
		router.update(root(route("gcs", "public", List.of("cache"), List.of(), "https://cdn.example.com")).withStorage("local").withStorageBucket(""));

		router.storeAt("", "cache", "a", "cache".getBytes());
		try (var zipped = router.zipAt("", "backups", "b.zip")) {
			router.backup("", "cache", zipped, null);
			zipped.commit();
		}

		assertThat(local.exists("", "cache", "a")).isFalse();
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");
		assertThat(router.getCdnUrl("", "cache", "a")).isEqualTo("https://cdn.example.com/default/cache/a");
		assertThat(local.exists("", "backups", "b.zip")).isTrue();
		assertThat(router.exists("", "backups", "b.zip")).isTrue();
		assertThat(router.getCdnUrl("", "backups", "b.zip")).isNull();
		assertThat(router.listTenants()).containsExactly("default");
		try (var zipped = router.streamZip("", "backups", "b.zip")) {
			assertThat(zipped.in("cache/a").readAllBytes()).isEqualTo("cache".getBytes());
		}
		assertThatThrownBy(() -> router.update(root(route("local", "", List.of("cache"), List.of(), "https://cdn.example.com"))))
			.isInstanceOf(IllegalArgumentException.class);
		router.update(root(route("local", "", List.of("backups"), List.of(), "")));
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:private");
		assertThat(router.exists("", "backups", "b.zip")).isTrue();
		assertThatThrownBy(() -> router.update(root(route("local", "private", List.of("backups"), List.of(), ""))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.update(root().withStorage("local"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testRuntimeConfig() {
		var configs = mock(ConfigCache.class);
		var listener = new AtomicReference<Consumer<ServerConfig>>();
		var initial = new AtomicReference<>(ServerConfig.builder().storage("gcs").build());
		doAnswer(i -> {
			listener.set(i.getArgument(0));
			listener.get().accept(initial.get());
			return null;
		}).when(configs).rootUpdate(any());
		var router = router(configs, gcs, s3);
		// An unconfigured server config must not prevent startup, so it can be fixed at runtime
		router.init();
		assertThatThrownBy(() -> router.storage("", "cache")).isInstanceOf(IllegalStateException.class);
		listener.get().accept(root(route("private", List.of("cache"), List.of(), "https://cdn.example.com")));
		assertThatThrownBy(() -> router.storage("", "cache")).isInstanceOf(IllegalStateException.class);

		initial.set(root(route("public", List.of("cache"), List.of(), "https://cdn.example.com")));
		router.init();
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");
		assertThat(router.getCdnUrl("", "cache", "a")).isEqualTo("https://cdn.example.com/default/cache/a");

		listener.get().accept(root(route("private", List.of("cache"), List.of(), "https://cdn.example.com")));
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");

		listener.get().accept(root());
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:private");
		assertThat(router.getCdnUrl("", "cache", "a")).isNull();

		listener.get().accept(root(route("s3", "public", List.of("cache"), List.of(), "https://cdn.example.com")));
		assertThat(bucket(router, "", "cache")).isEqualTo("s3:public");

		listener.get().accept(ServerConfig.builder().build());
		assertThat(bucket(router, "", "cache")).isEqualTo("local");
	}

	@Test
	void testServerOverrides() {
		var props = new Props();
		props.getOverride().getServer().setStorage("s3");
		props.getOverride().getServer().setStorageBucket("override");
		props.getOverride().getServer().setStorageRoutes(List.of(route("gcs", "public", List.of("cache"), List.of(), "https://cdn.example.com")));
		var router = router();
		router.update(root().wrap(props));

		assertThat(bucket(router, "", "backups")).isEqualTo("s3:override");
		assertThat(bucket(router, "", "cache")).isEqualTo("gcs:public");
	}

	@Test
	void testServerOverrideEmptyRoutes() {
		var props = new Props();
		props.getOverride().getServer().setStorageRoutes(List.of());
		var wrapped = root(route("public", List.of("cache"), List.of(), "https://cdn.example.com")).wrap(props);
		assertThat(wrapped.getStorageRoutes()).isEmpty();
		assertThat(root(route("public", List.of("cache"), List.of(), "")).wrap(new Props()).getStorageRoutes()).hasSize(1);
	}

	@Test
	void testServerOverrideLocalStorage() throws IOException {
		var router = router();

		var props = new Props();
		props.getOverride().getServer().setStorage("local");
		var wrapped = root().wrap(props);
		assertThat(wrapped.getStorage()).isEqualTo("local");
		assertThat(wrapped.getStorageBucket()).isEmpty();
		router.update(wrapped);
		router.storeAt("", "backups", "b", "backup".getBytes());
		assertThat(local.exists("", "backups", "b")).isTrue();

		var blank = new Props();
		blank.getOverride().getServer().setStorageBucket("");
		assertThat(root().withStorage("local").wrap(blank).getStorageBucket()).isEmpty();
		assertThat(root().wrap(new Props()).getStorageBucket()).isEqualTo("private");
	}

	@Test
	void testCdnUrlUnconfigured() {
		var router = router(mock(ConfigCache.class), gcs, s3);
		assertThat(router.getCdnUrl("", "cache", "a")).isNull();
		assertThatThrownBy(() -> router.storage("", "cache")).isInstanceOf(IllegalStateException.class);
	}
}
