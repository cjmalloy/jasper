package jasper.component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import jasper.repository.spec.QualifiedTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics.monitor;
import static jasper.repository.spec.QualifiedTag.tagOriginSelector;

@Component
public class ScriptExecutorFactory {
	private static final Logger logger = LoggerFactory.getLogger(ScriptExecutorFactory.class);
	private static final int DEFAULT_QUEUE_CAPACITY = 10000;

	@Autowired
	MeterRegistry meterRegistry;
	
	@Autowired
	ConfigCache configs;

	private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();

	public ExecutorService get(String tag, String origin) {
		return executors.computeIfAbsent(tag + origin, k -> {
			int maxPoolSize = getMaxPoolSize(tag, origin);
			logger.info("{} Creating dynamic script executor for {} with max pool size {}", origin, k, maxPoolSize);
			var executor = new ThreadPoolTaskExecutor();
			executor.setCorePoolSize(1);
			executor.setMaxPoolSize(maxPoolSize);
			executor.setQueueCapacity(DEFAULT_QUEUE_CAPACITY);
			executor.setThreadNamePrefix("script-" + k + "-");
			executor.setRejectedExecutionHandler((Runnable r, ThreadPoolExecutor e) -> {
				throw new RejectedExecutionException("Script " + k +
						" task " + r.toString() +
						" rejected from " + e.toString() +
						" queue size " + DEFAULT_QUEUE_CAPACITY);
			});
			executor.setWaitForTasksToCompleteOnShutdown(true);
			executor.setAwaitTerminationSeconds(60);
			executor.initialize();
			return monitor(meterRegistry, executor.getThreadPoolExecutor(), "scriptExecutor", "script", Tags.of(
					"tag", tag,
					"origin", origin));
		});
	}
	
	/**
	 * Get the max pool size for a script executor based on configuration hierarchy:
	 * 1. Per-origin SecurityConfig scriptLimits (highest priority)
	 * 2. ServerConfig maxConcurrentScriptsPerOrigin (with env var override)
	 * 3. Default fallback (4)
	 * 
	 * Takes the smallest non-null value when multiple sources are configured.
	 */
	private int getMaxPoolSize(String tag, String origin) {
		Integer limit = null;
		
		// Check per-origin limits from SecurityConfig
		try {
			var securityConfig = configs.security(origin);
			if (securityConfig != null && securityConfig.getScriptLimits() != null) {
				var selector = tagOriginSelector(tag + origin);
				for (var entry : securityConfig.getScriptLimits().entrySet()) {
					var pattern = tagOriginSelector(entry.getKey());
					if (pattern.captures(selector)) {
						int perOriginLimit = entry.getValue();
						limit = (limit == null) ? perOriginLimit : Math.min(limit, perOriginLimit);
						logger.debug("{} Found script limit {} for pattern {}", origin, perOriginLimit, entry.getKey());
					}
				}
			}
		} catch (Exception e) {
			logger.debug("{} Could not load security config for script limits", origin, e);
		}
		
		// Check server-wide limits (with env var override)
		try {
			int serverLimit = configs.root().getMaxConcurrentScriptsPerOrigin();
			if (serverLimit > 0) {
				limit = (limit == null) ? serverLimit : Math.min(limit, serverLimit);
			}
		} catch (Exception e) {
			logger.debug("Could not load server config for script limits", e);
		}
		
		// Fall back to default
		return (limit != null && limit > 0) ? limit : 4;
	}

	@PreDestroy
	public void cleanup() {
		logger.info("Shutting down {} dynamic script executors and their metrics", executors.size());
		executors.values().forEach(executor -> {
			try {
				executor.shutdown();
			} catch (Exception e) {
				logger.warn("Error shutting down script executor", e);
			}
		});
		executors.clear();
	}
}
