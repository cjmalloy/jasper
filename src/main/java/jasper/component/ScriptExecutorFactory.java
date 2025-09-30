package jasper.component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
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

@Component
public class ScriptExecutorFactory {
	private static final Logger logger = LoggerFactory.getLogger(ScriptExecutorFactory.class);

	@Autowired
	MeterRegistry meterRegistry;

	private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();

	public ExecutorService get(String tag, String origin) {
		return executors.computeIfAbsent(tag + origin, k -> {
			logger.info("{} Creating dynamic script executor for {}", origin, k);
			var executor = new ThreadPoolTaskExecutor();
			executor.setCorePoolSize(2);
			executor.setMaxPoolSize(4);
			executor.setQueueCapacity(1000);
			executor.setThreadNamePrefix("script-" + k + "-");
			executor.setRejectedExecutionHandler((Runnable r, ThreadPoolExecutor e) -> {
				throw new RejectedExecutionException("Script " + k +
						" task " + r.toString() +
						" rejected from " + e.toString() +
						" queue size " + 1000);
			});
			executor.setWaitForTasksToCompleteOnShutdown(true);
			executor.setAwaitTerminationSeconds(60);
			executor.initialize();
			return monitor(meterRegistry, executor.getThreadPoolExecutor(), "scriptExecutor", "script", Tags.of(
					"tag", tag,
					"origin", origin));
		});
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
