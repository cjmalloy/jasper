package jasper.component;

import jasper.config.CacheConfig;
import jasper.config.Props;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigCacheEvictTest {

	ConfigCache configCache = new ConfigCache();

	@BeforeEach
	void init() {
		configCache.props = new Props();
		configCache.cacheManager = new CacheConfig().cacheManager();
	}

	void put(String cache, Object key) {
		configCache.cacheManager.getCache(cache).put(key, "value");
	}

	boolean cached(String cache, Object key) {
		return configCache.cacheManager.getCache(cache).get(key) != null;
	}

	@Test
	void testClearUserCacheByOrigin() {
		put("user-cache", "+user/alice");
		put("user-cache", "+user/alice@a");
		put("user-cache", "+user/alice@b");
		put("user-cache", "+user");
		put("user-dto-cache", "+user/alice@a");
		put("user-dto-cache", "+user/alice@b");
		put("external-user-cache", List.of("@a", "alice@example.com"));
		put("external-user-cache", List.of("@b", "alice@example.com"));
		put("user-dto-page-cache", "0_20_UNSORTED_null_@b_null_null_null_null");

		configCache.clearUserCache("@a");

		assertThat(cached("user-cache", "+user/alice")).isTrue();
		assertThat(cached("user-cache", "+user/alice@a")).isFalse();
		assertThat(cached("user-cache", "+user/alice@b")).isTrue();
		assertThat(cached("user-cache", "+user")).isTrue();
		assertThat(cached("user-dto-cache", "+user/alice@a")).isFalse();
		assertThat(cached("user-dto-cache", "+user/alice@b")).isTrue();
		assertThat(cached("external-user-cache", List.of("@a", "alice@example.com"))).isFalse();
		assertThat(cached("external-user-cache", List.of("@b", "alice@example.com"))).isTrue();
		assertThat(cached("user-dto-page-cache", "0_20_UNSORTED_null_@b_null_null_null_null")).isFalse();
	}

	@Test
	void testClearUserCacheDefaultOrigin() {
		put("user-cache", "+user/alice");
		put("user-cache", "+user/alice@a");
		put("user-cache", "+user");

		configCache.clearUserCache("default");

		assertThat(cached("user-cache", "+user/alice")).isFalse();
		assertThat(cached("user-cache", "+user")).isFalse();
		assertThat(cached("user-cache", "+user/alice@a")).isTrue();
	}

	@Test
	void testClearUserCacheLocalOrigin() {
		configCache.props.setLocalOrigin("@local");
		put("user-cache", "+user");
		put("user-cache", "+user/alice@a");

		configCache.clearUserCache("@local");

		assertThat(cached("user-cache", "+user")).isFalse();
		assertThat(cached("user-cache", "+user/alice@a")).isTrue();
	}

	@Test
	void testClearPluginCacheByOrigin() {
		put("plugin-cache", "plugin/test@a");
		put("plugin-cache", "plugin/test@a.b");
		put("plugin-config-cache", "plugin/test@a");
		put("plugin-config-cache", "plugin/test");
		put("plugin-dto-cache", "plugin/test@a");
		put("plugin-dto-cache", "plugin/test@b");

		configCache.clearPluginCache("@a");

		assertThat(cached("plugin-cache", "plugin/test@a")).isFalse();
		assertThat(cached("plugin-cache", "plugin/test@a.b")).isTrue();
		assertThat(cached("plugin-config-cache", "plugin/test@a")).isFalse();
		assertThat(cached("plugin-config-cache", "plugin/test")).isTrue();
		assertThat(cached("plugin-dto-cache", "plugin/test@a")).isFalse();
		assertThat(cached("plugin-dto-cache", "plugin/test@b")).isTrue();
	}

	@Test
	void testClearTemplateCacheByOrigin() {
		put("template-cache", "_config/server");
		put("template-cache", "config/test@a");
		put("template-cache", "config/test@b");
		put("template-config-cache", "config/test@a");
		put("template-dto-cache", "config/test@b");

		configCache.clearTemplateCache("@a");

		assertThat(cached("template-cache", "_config/server")).isTrue();
		assertThat(cached("template-cache", "config/test@a")).isFalse();
		assertThat(cached("template-cache", "config/test@b")).isTrue();
		assertThat(cached("template-config-cache", "config/test@a")).isFalse();
		assertThat(cached("template-dto-cache", "config/test@b")).isTrue();
	}

	@Test
	void testClearTemplateCacheLocalConfig() {
		put("template-cache", "_config/server");
		put("template-cache", "_config/index");

		configCache.clearTemplateCache("");

		assertThat(cached("template-cache", "_config/server")).isFalse();
		assertThat(cached("template-cache", "_config/index")).isFalse();
	}

	@Test
	void testClearTemplateCacheSecurityInheritedBySubOrigins() {
		put("template-cache-wrapped", "_config/security@a.b");

		configCache.clearTemplateCache("@b");
		assertThat(cached("template-cache-wrapped", "_config/security@a.b")).isTrue();

		configCache.clearTemplateCache("@a");
		assertThat(cached("template-cache-wrapped", "_config/security@a.b")).isFalse();
	}

	@Test
	void testClearTemplateCacheSecurityRootOrigin() {
		put("template-cache-wrapped", "_config/security@a");

		configCache.clearTemplateCache("");

		assertThat(cached("template-cache-wrapped", "_config/security@a")).isFalse();
	}
}
