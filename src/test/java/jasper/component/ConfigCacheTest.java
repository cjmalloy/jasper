package jasper.component;

import jasper.config.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCacheTest {
	private static final String[] USER_CACHES = {
		"user-cache",
		"user-dto-cache",
		"user-dto-page-cache",
		"external-user-cache"
	};
	private static final String[] PLUGIN_CACHES = {
		"plugin-cache",
		"plugin-config-cache",
		"plugin-dto-cache",
		"plugin-dto-page-cache"
	};
	private static final String[] TEMPLATE_CACHES = {
		"template-cache",
		"template-config-cache",
		"template-cache-wrapped",
		"template-schemas-cache",
		"template-defaults-cache",
		"template-dto-cache",
		"template-dto-page-cache"
	};

	ConfigCache configs;

	@BeforeEach
	void setup() {
		configs = new ConfigCache();
		configs.props = new Props();
		configs.props.setLocalOrigin("@tenant");
		configs.cacheManager = new ConcurrentMapCacheManager();
	}

	@Test
	void clearCachesByOrigin() {
		assertScopedClear(configs::clearUserCache, USER_CACHES);
		assertScopedClear(configs::clearPluginCache, PLUGIN_CACHES);
		assertScopedClear(configs::clearTemplateCache, TEMPLATE_CACHES);
		assertScopedClear(configs::clearConfigCache, "config-cache");
	}

	@Test
	void clearChildEvictsCoveringOriginWildcard() {
		var cache = configs.cacheManager.getCache("config-cache");
		assertThat(cache).isNotNull();

		put("config-cache", "@tenant", "parent");
		put("config-cache", "@tenant.*", "subtree");
		put("config-cache", "@other.*", "other");

		configs.clearConfigCache("@tenant.child");

		assertThat(cache.get("parent")).isNotNull();
		assertThat(cache.get("subtree")).isNull();
		assertThat(cache.get("other")).isNotNull();
	}

	@Test
	void clearLocalOriginEvictsIndexWithoutChangingItsKey() {
		var cache = configs.cacheManager.getCache("template-cache");
		assertThat(cache).isNotNull();
		var key = configs.trackLocalCacheKey("template-cache", "_config/index");
		cache.put(key, "index");

		configs.clearTemplateCache("@tenant.child");

		assertThat(cache.get("_config/index")).isNotNull();

		configs.clearTemplateCache("@tenant");

		assertThat(cache.get("_config/index")).isNull();
		assertThat(key).isEqualTo("_config/index");
	}

	@Test
	void generatedKeysRemainUnchanged() {
		var key = configs.trackGeneratedCacheKey("external-user-cache", "@tenant", "@tenant", "external-id");

		assertThat(key).isEqualTo(SimpleKeyGenerator.generateKey("@tenant", "external-id"));
	}

	@Test
	void sharedKeyIsEvictedForEveryTrackedOrigin() {
		var cache = configs.cacheManager.getCache("user-cache");
		assertThat(cache).isNotNull();
		put("user-cache", "", "+user");
		put("user-cache", "@tenant", "+user");

		configs.clearUserCache("@tenant");

		assertThat(cache.get("+user")).isNull();
	}

	private void assertScopedClear(Consumer<String> clear, String... cacheNames) {
		for (var cacheName : cacheNames) {
			var cache = configs.cacheManager.getCache(cacheName);
			assertThat(cache).isNotNull();

			put(cacheName, "", "root");
			put(cacheName, "@tenant", "tenant");
			put(cacheName, "@tenant.child", "child");
			put(cacheName, "@other", "other");
			put(cacheName, "@*", "wildcard");
		}

		clear.accept("@tenant");

		for (var cacheName : cacheNames) {
			Cache cache = configs.cacheManager.getCache(cacheName);
			assertThat(cache).isNotNull();
			assertThat(cache.get("root")).isNotNull();
			assertThat(cache.get("tenant")).isNull();
			assertThat(cache.get("child")).isNull();
			assertThat(cache.get("other")).isNotNull();
			assertThat(cache.get("wildcard")).isNull();
			cache.clear();
		}
	}

	private void put(String cacheName, String origin, String key) {
		var cache = configs.cacheManager.getCache(cacheName);
		assertThat(cache).isNotNull();
		cache.put(configs.trackCacheKey(cacheName, origin, key), key);
	}
}
