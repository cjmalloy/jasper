package jasper.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.function.Consumer;

import static jasper.component.ConfigCache.originKey;
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
		configs.cacheManager = new CaffeineCacheManager();
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

		cache.put(originKey("@tenant", "parent"), "parent");
		cache.put(originKey("@tenant.*", "subtree"), "subtree");
		cache.put(originKey("@other.*", "other"), "other");

		configs.clearConfigCache("@tenant.child");

		assertThat(cache.get(originKey("@tenant", "parent"))).isNotNull();
		assertThat(cache.get(originKey("@tenant.*", "subtree"))).isNull();
		assertThat(cache.get(originKey("@other.*", "other"))).isNotNull();
	}

	private void assertScopedClear(Consumer<String> clear, String... cacheNames) {
		for (var cacheName : cacheNames) {
			var cache = configs.cacheManager.getCache(cacheName);
			assertThat(cache).isNotNull();

			cache.put(originKey("", "root"), "root");
			cache.put(originKey("@tenant", "tenant"), "tenant");
			cache.put(originKey("@tenant.child", "child"), "child");
			cache.put(originKey("@other", "other"), "other");
			cache.put(originKey("@*", "wildcard"), "wildcard");
		}

		clear.accept("@tenant");

		for (var cacheName : cacheNames) {
			Cache cache = configs.cacheManager.getCache(cacheName);
			assertThat(cache).isNotNull();
			assertThat(cache.get(originKey("", "root"))).isNotNull();
			assertThat(cache.get(originKey("@tenant", "tenant"))).isNull();
			assertThat(cache.get(originKey("@tenant.child", "child"))).isNull();
			assertThat(cache.get(originKey("@other", "other"))).isNotNull();
			assertThat(cache.get(originKey("@*", "wildcard"))).isNull();
			cache.clear();
		}
	}
}
