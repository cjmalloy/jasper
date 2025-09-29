package jasper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.util.CallerBlocksPolicy;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfiguration implements AsyncConfigurer, SchedulingConfigurer {
	private final Logger logger = LoggerFactory.getLogger(AsyncConfiguration.class);

	@Autowired
	TaskExecutionProperties taskExecutionProperties;

	@Autowired
	TaskSchedulingProperties taskSchedulingProperties;

	@Profile("!redis")
	@Bean("integration")
	public ThreadPoolTaskExecutor taskExecutor() {
		var executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("int-");
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(32);
		executor.setQueueCapacity(0);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}

	@Profile("redis")
	@Bean("integration")
	public TaskExecutor taskExecutorRedis() {
		var executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("int-");
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(4);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.setRejectedExecutionHandler(new CallerBlocksPolicy(60_000));
		executor.initialize();
		return executor;
	}

	/**
	 * Scripts executor - for delta and cron script execution.
	 * Dynamically sized to handle script execution load.
	 */
	@Bean("scriptsExecutor")
	public Executor getScriptsExecutor() {
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
		return executor;
	}

	/**
	 * WebSocket executor - for WebSocket message processing.
	 * Fixed size pool for WebSocket connections.
	 */
	@Bean("websocketExecutor")
	public Executor getWebsocketExecutor() {
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
		return executor;
	}

	/**
	 * General async executor - for general purpose async operations.
	 * This is the default executor for @Async methods.
	 */
	@Bean("taskExecutor")
	@Override
	public Executor getAsyncExecutor() {
		logger.debug("Creating General Async Thread Pool Executor");
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(taskExecutionProperties.getPool().getCoreSize());
		executor.setMaxPoolSize(taskExecutionProperties.getPool().getMaxSize());
		executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
		executor.setThreadNamePrefix(taskExecutionProperties.getThreadNamePrefix());
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		return executor;
	}

	/**
	 * Scheduler executor - for scheduled tasks and cron jobs.
	 * Single-threaded scheduler with queue for sequential task execution.
	 */
	@Bean("taskScheduler")
	public ThreadPoolTaskScheduler getSchedulerExecutor() {
		logger.debug("Creating Scheduler Thread Pool");
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(taskSchedulingProperties.getPool().getSize());
		scheduler.setThreadNamePrefix(taskSchedulingProperties.getThreadNamePrefix());
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(60);
		scheduler.initialize();
		return scheduler;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.setTaskScheduler(getSchedulerExecutor());
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return new SimpleAsyncUncaughtExceptionHandler();
	}
}
