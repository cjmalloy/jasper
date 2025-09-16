package jasper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.util.CallerBlocksPolicy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class CronConfiguration {
    private final Logger logger = LoggerFactory.getLogger(CronConfiguration.class);

	@Bean("cronScheduler")
	public TaskScheduler taskScheduler() {
		var executor = new ThreadPoolTaskScheduler();
		executor.setThreadNamePrefix("cron-");
		executor.setPoolSize(1);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.setRejectedExecutionHandler(new CallerBlocksPolicy(60_000));
		executor.initialize();
		return executor;
	}

}
