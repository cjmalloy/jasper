package jasper.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ScrapeConfig {
	private static final Logger logger = LoggerFactory.getLogger(ScrapeConfig.class);

	@Bean("scrapePoolTaskExecutor")
	public Executor scrapePoolTaskExecutor() {
		final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("scrape-");
		executor.setRejectedExecutionHandler((r, executor1) -> {
			logger.error("rejected");
		});
		executor.setAllowCoreThreadTimeOut(true);
		executor.setAwaitTerminationSeconds(5);
		executor.initialize();
		return new ConcurrentTaskExecutor(executor);
	}

	@Bean
	public CloseableHttpClient httpClient() {
		int timeout = 5 * 60 * 1000; // 5
		return HttpClients
			.custom()
			.disableCookieManagement()
			.setDefaultRequestConfig(RequestConfig
				.custom()
				.setConnectTimeout(timeout)
				.setConnectionRequestTimeout(timeout)
				.setSocketTimeout(timeout)
				.build())
			.build();
	}
}
