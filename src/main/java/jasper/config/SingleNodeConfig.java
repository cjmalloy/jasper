package jasper.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Profile("!redis")
@Configuration
public class SingleNodeConfig {

	@Qualifier("integration")
	@Autowired
	TaskExecutor taskExecutor;

	@Autowired
	MessageChannel cursorTxChannel;

	@Autowired
	MessageChannel cursorRxChannel;

	@Autowired
	MessageChannel refTxChannel;

	@Autowired
	MessageChannel refRxChannel;

	@Autowired
	MessageChannel tagTxChannel;

	@Autowired
	MessageChannel tagRxChannel;

	@Autowired
	MessageChannel responseTxChannel;

	@Autowired
	MessageChannel responseRxChannel;

	@Autowired
	MessageChannel userTxChannel;

	@Autowired
	MessageChannel userRxChannel;

	@Autowired
	MessageChannel extTxChannel;

	@Autowired
	MessageChannel extRxChannel;

	@Autowired
	MessageChannel pluginTxChannel;

	@Autowired
	MessageChannel pluginRxChannel;

	@Autowired
	MessageChannel templateTxChannel;

	@Autowired
	MessageChannel templateRxChannel;

	@Bean("integration")
	public TaskExecutor taskExecutor() {
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

	@Profile("!test")
	@Bean
	public CaffeineCacheManager cacheManager() {
		var cacheManager = new CaffeineCacheManager();
		cacheManager.registerCustomCache("oembed-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.HOURS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("config-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.build());
		cacheManager.registerCustomCache("user-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(15, TimeUnit.MINUTES)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("plugin-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("plugin-config-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("plugin-metadata-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("all-plugins-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-config-cache", Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-cache-wrapped", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("template-schemas-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		cacheManager.registerCustomCache("all-templates-cache", Caffeine.newBuilder()
			.maximumSize(1)
			.expireAfterAccess(1, TimeUnit.DAYS)
			.recordStats()
			.build());
		return cacheManager;
	}

	@Bean
	public IntegrationFlow directCursorFlow() {
		return IntegrationFlows.from(cursorTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(cursorRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directRefFlow() {
		return IntegrationFlows.from(refTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(refRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directTagFlow() {
		return IntegrationFlows.from(tagTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(tagRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directResponseFlow() {
		return IntegrationFlows.from(responseTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(responseRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directUserFlow() {
		return IntegrationFlows.from(userTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(userRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directExtFlow() {
		return IntegrationFlows.from(extTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(extRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directPluginFlow() {
		return IntegrationFlows.from(pluginTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(pluginRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directTemplateFlow() {
		return IntegrationFlows.from(templateTxChannel)
							   .channel(new ExecutorChannel(taskExecutor))
							   .channel(templateRxChannel)
							   .get();
	}
}
