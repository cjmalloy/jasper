package jasper.config;

import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.integration.util.CallerBlocksPolicy;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics.monitor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfiguration implements AsyncConfigurer, SchedulingConfigurer {
	private final Logger logger = LoggerFactory.getLogger(AsyncConfiguration.class);

	@Autowired
	TaskExecutionProperties taskExecutionProperties;

	@Autowired
	TaskSchedulingProperties taskSchedulingProperties;

	@Autowired
	MeterRegistry meterRegistry;

	@Profile("!redis")
	@Bean("integrationExecutor")
	public ExecutorService getIntegrationExecutor() {
		var executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("int-");
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(32);
		executor.setQueueCapacity(0);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return monitor(meterRegistry, executor.getThreadPoolExecutor(), "integrationExecutor", "task");
	}

	@Profile("redis")
	@Bean("integrationExecutor")
	public ExecutorService getRedisExecutor() {
		var executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("int-");
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(4);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.setRejectedExecutionHandler(new CallerBlocksPolicy(60_000));
		executor.initialize();
		return monitor(meterRegistry, executor.getThreadPoolExecutor(), "integrationExecutor", "task");
	}

	@Bean("websocketExecutor")
	public ExecutorService getWebsocketExecutor() {
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("websocket-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		return monitor(meterRegistry, executor.getThreadPoolExecutor(), "websocketExecutor", "task");
	}

	@Bean("taskExecutor")
	@Override
	public ExecutorService getAsyncExecutor() {
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(taskExecutionProperties.getPool().getCoreSize());
		executor.setMaxPoolSize(taskExecutionProperties.getPool().getMaxSize());
		executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
		executor.setThreadNamePrefix(taskExecutionProperties.getThreadNamePrefix());
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		return monitor(meterRegistry, executor.getThreadPoolExecutor(), "taskExecutor", "task");
	}

	@Bean("taskScheduler")
	public ThreadPoolTaskScheduler getSchedulerExecutor() {
		var scheduler = new ThreadPoolTaskScheduler();
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
