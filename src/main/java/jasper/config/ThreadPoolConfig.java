package jasper.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool configuration split by component with metrics support.
 * This replaces the general purpose pools with component-specific pools
 * for better monitoring and resource management.
 */
@Configuration
public class ThreadPoolConfig {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolConfig.class);

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Scripts executor - for delta and cron script execution.
     * Dynamically sized to handle script execution load.
     */
    @Bean(name = "scriptsExecutor")
    public Executor scriptsExecutor() {
        logger.debug("Creating Scripts Thread Pool Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("scripts-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        // Add metrics
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), "scripts",
            Tag.of("type", "script"));
        
        return executor;
    }

    /**
     * WebSocket executor - for WebSocket message processing.
     * Fixed size pool for WebSocket connections.
     */
    @Bean(name = "websocketExecutor")
    public Executor websocketExecutor() {
        logger.debug("Creating WebSocket Thread Pool Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("websocket-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        // Add metrics
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), "websocket",
            Tag.of("type", "websocket"));
        
        return executor;
    }

    /**
     * Integration executor - for Spring Integration flows.
     * Used by SingleNodeConfig for message channel processing.
     */
    @Bean(name = "integrationExecutor")
    public Executor integrationExecutor() {
        logger.debug("Creating Integration Thread Pool Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("integration-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        // Add metrics
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), "integration",
            Tag.of("type", "integration"));
        
        return executor;
    }

    /**
     * General async executor - for general purpose async operations.
     * This is the default executor for @Async methods.
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        logger.debug("Creating General Async Thread Pool Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        // Add metrics
        ExecutorServiceMetrics.monitor(meterRegistry, executor.getThreadPoolExecutor(), "async",
            Tag.of("type", "async"));
        
        return executor;
    }

    /**
     * Scheduler executor - for scheduled tasks and cron jobs.
     * Single-threaded scheduler with queue for sequential task execution.
     */
    @Bean(name = "schedulerExecutor")
    public ThreadPoolTaskScheduler schedulerExecutor() {
        logger.debug("Creating Scheduler Thread Pool");
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        
        // Add metrics for scheduled thread pool
        ExecutorServiceMetrics.monitor(meterRegistry, scheduler.getScheduledThreadPoolExecutor(), "scheduler",
            Tag.of("type", "scheduler"));
        
        return scheduler;
    }
}