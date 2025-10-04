package jasper.component;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics.monitor;

@Component
public class ScriptExecutorFactory {
	private static final Logger logger = LoggerFactory.getLogger(ScriptExecutorFactory.class);

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	ConfigCache configs;

	private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
	public ExecutorService get(String tag, String origin) {
		return executors.computeIfAbsent(tag + origin, k -> {
			int maxPoolSize = configs.security(origin).scriptLimit(tag, origin);
			logger.info("{} Creating virtual thread executor for {} with script limit {}", origin, k, maxPoolSize);
			return monitor(meterRegistry, Executors.newVirtualThreadPerTaskExecutor(), "scriptExecutor", "script");
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
