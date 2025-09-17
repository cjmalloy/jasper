package jasper.component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Factory for creating and managing dynamic thread pools for script execution.
 * Allows creating separate pools per script type or tenant for better isolation
 * and resource management.
 */
@Component
public class ScriptExecutorFactory {
    private static final Logger logger = LoggerFactory.getLogger(ScriptExecutorFactory.class);

    @Autowired
    private MeterRegistry meterRegistry;

    private final Map<String, ThreadPoolTaskExecutor> executors = new ConcurrentHashMap<>();

    /**
     * Creates or returns an existing thread pool executor for the given script type.
     * This allows for isolation between different script types (delta, cron, etc.)
     * and better resource management.
     *
     * @param scriptType The type of script (e.g., "delta", "cron", "python", "javascript")
     * @param origin The origin/tenant (optional, for multi-tenant isolation)
     * @return A thread pool executor for the specified script type
     */
    public Executor getExecutorForScript(String scriptType, String origin) {
        String key = origin != null ? scriptType + ":" + origin : scriptType;
        
        return executors.computeIfAbsent(key, k -> {
            logger.info("Creating dynamic script executor for {}", k);
            
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            
            // Configure pool based on script type
            switch (scriptType.toLowerCase()) {
                case "delta":
                    // Delta scripts need more resources as they can be CPU intensive
                    executor.setCorePoolSize(2);
                    executor.setMaxPoolSize(8);
                    executor.setQueueCapacity(20);
                    break;
                case "cron":
                    // Cron scripts are typically less frequent but may be long-running
                    executor.setCorePoolSize(1);
                    executor.setMaxPoolSize(4);
                    executor.setQueueCapacity(10);
                    break;
                case "python":
                    // Python scripts may need more memory and processing time
                    executor.setCorePoolSize(1);
                    executor.setMaxPoolSize(6);
                    executor.setQueueCapacity(15);
                    break;
                case "javascript":
                    // JavaScript scripts are typically lighter weight
                    executor.setCorePoolSize(2);
                    executor.setMaxPoolSize(10);
                    executor.setQueueCapacity(25);
                    break;
                default:
                    // Default configuration for unknown script types
                    executor.setCorePoolSize(1);
                    executor.setMaxPoolSize(4);
                    executor.setQueueCapacity(10);
            }
            
            executor.setThreadNamePrefix("script-" + k.replace(":", "-") + "-");
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(60);
            executor.initialize();
            
            // Add metrics for the new executor
            ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), 
                "script_executor",
                Tag.of("type", scriptType),
                Tag.of("origin", origin != null ? origin : "default"));
            
            return executor;
        });
    }

    /**
     * Creates or returns an executor specifically for delta scripts.
     */
    public Executor getDeltaExecutor(String origin) {
        return getExecutorForScript("delta", origin);
    }

    /**
     * Creates or returns an executor specifically for cron scripts.
     */
    public Executor getCronExecutor(String origin) {
        return getExecutorForScript("cron", origin);
    }

    /**
     * Creates or returns an executor for a specific script language.
     */
    public Executor getLanguageExecutor(String language, String origin) {
        return getExecutorForScript(language, origin);
    }

    /**
     * Gets statistics for all managed executors.
     */
    public Map<String, ExecutorStats> getExecutorStats() {
        Map<String, ExecutorStats> stats = new ConcurrentHashMap<>();
        
        executors.forEach((key, executor) -> {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            stats.put(key, new ExecutorStats(
                pool.getCorePoolSize(),
                pool.getMaximumPoolSize(),
                pool.getActiveCount(),
                pool.getPoolSize(),
                pool.getQueue().size(),
                pool.getCompletedTaskCount(),
                pool.getTaskCount()
            ));
        });
        
        return stats;
    }

    /**
     * Cleanup all dynamic executors on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down {} dynamic script executors", executors.size());
        
        executors.values().forEach(executor -> {
            try {
                executor.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down script executor", e);
            }
        });
        
        executors.clear();
    }

    /**
     * Statistics for a thread pool executor.
     */
    public record ExecutorStats(
        int corePoolSize,
        int maxPoolSize,
        int activeCount,
        int poolSize,
        int queueSize,
        long completedTaskCount,
        long taskCount
    ) {}
}