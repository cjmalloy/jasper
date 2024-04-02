package jasper.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

@Profile("!test & !no-cache")
@Configuration
public class CacheConfig {

	@Bean
	public CaffeineCacheManager cacheManager() {
		var cacheManager = new CaffeineCacheManager();
		cacheManager.registerCustomCache("oembed-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.HOURS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("config-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.build());
		cacheManager.registerCustomCache("user-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(15, TimeUnit.MINUTES)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("plugin-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("plugin-config-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("plugin-metadata-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("all-plugins-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-config-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-cache-wrapped", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-schemas-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("all-templates-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		return cacheManager;
	}
}
