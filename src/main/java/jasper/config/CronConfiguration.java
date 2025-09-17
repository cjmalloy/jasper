package jasper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class CronConfiguration {
    private final Logger logger = LoggerFactory.getLogger(CronConfiguration.class);

    @Autowired
    @Qualifier("schedulerExecutor")
    private ThreadPoolTaskScheduler schedulerExecutor;

    @Bean("cronScheduler")
    public TaskScheduler taskScheduler() {
        logger.debug("Using component-specific scheduler for cron tasks");
        return schedulerExecutor;
    }
}
