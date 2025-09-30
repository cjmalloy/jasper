package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import jasper.component.ConfigCache;
import jasper.domain.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;

import java.time.Duration;

import static java.time.Duration.ofSeconds;

@Configuration
public class BulkheadConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(BulkheadConfiguration.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Props props;

	@Bean
	public Bulkhead globalScriptBulkhead() {
		var maxConcurrent = getMaxConcurrentScripts();
		logger.info("Creating global script execution bulkhead with {} permits", maxConcurrent);
		
		var bulkheadConfig = BulkheadConfig.custom()
			.maxConcurrentCalls(maxConcurrent)
			.maxWaitDuration(ofSeconds(60))
			.build();
		
		return Bulkhead.of("global-script-execution", bulkheadConfig);
	}

	@Bean
	public Bulkhead globalCronScriptBulkhead() {
		var maxConcurrent = getMaxConcurrentCronScripts();
		logger.info("Creating global cron script execution bulkhead with {} permits", maxConcurrent);
		
		var bulkheadConfig = BulkheadConfig.custom()
			.maxConcurrentCalls(maxConcurrent)
			.maxWaitDuration(ofSeconds(60))
			.build();
		
		return Bulkhead.of("global-cron-script-execution", bulkheadConfig);
	}

	@Bean
	public Bulkhead globalReplicationBulkhead() {
		var maxConcurrent = getMaxConcurrentReplication();
		logger.info("Creating global replication bulkhead with {} permits", maxConcurrent);
		
		var bulkheadConfig = BulkheadConfig.custom()
			.maxConcurrentCalls(maxConcurrent)
			.maxWaitDuration(ofSeconds(30))
			.build();
		
		return Bulkhead.of("global-replication", bulkheadConfig);
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<Template> templateMessage) {
		var template = templateMessage.getPayload();
		if (template.getTag() != null && template.getTag().startsWith("_config/server")) {
			logger.debug("Server config template updated, updating bulkhead configurations");
			updateBulkheadConfig(globalScriptBulkhead(), this::getMaxConcurrentScripts, "script execution");
			updateBulkheadConfig(globalCronScriptBulkhead(), this::getMaxConcurrentCronScripts, "cron script execution");
			updateBulkheadConfig(globalReplicationBulkhead(), this::getMaxConcurrentReplication, "replication");
			updateBulkheadConfig(globalHttpBulkhead(), this::getMaxConcurrentRequests, "HTTP request");
		}
	}

	private void updateBulkheadConfig(Bulkhead bulkhead, java.util.function.IntSupplier maxSupplier, String name) {
		var maxConcurrent = maxSupplier.getAsInt();
		logger.info("Updating global {} bulkhead to {} permits", name, maxConcurrent);
		
		var newConfig = BulkheadConfig.custom()
			.maxConcurrentCalls(maxConcurrent)
			.maxWaitDuration(bulkhead.getBulkheadConfig().getMaxWaitDuration())
			.build();
		
		bulkhead.changeConfig(newConfig);
	}

	@Bean
	public Bulkhead globalHttpBulkhead() {
		var maxConcurrent = getMaxConcurrentRequests();
		logger.info("Creating global HTTP request bulkhead with {} permits", maxConcurrent);

		var bulkheadConfig = BulkheadConfig.custom()
			.maxConcurrentCalls(maxConcurrent)
			.maxWaitDuration(Duration.ofMillis(0)) // Don't wait, fail fast
			.build();

		return Bulkhead.of("http-global", bulkheadConfig);
	}

	private int getMaxConcurrentScripts() {
		var serverConfig = configs.root();
		return props.getMaxConcurrentScripts() != null 
			? props.getMaxConcurrentScripts() 
			: serverConfig.getMaxConcurrentScripts();
	}

	private int getMaxConcurrentCronScripts() {
		var serverConfig = configs.root();
		return props.getMaxConcurrentCronScripts() != null 
			? props.getMaxConcurrentCronScripts() 
			: serverConfig.getMaxConcurrentCronScripts();
	}

	private int getMaxConcurrentReplication() {
		var serverConfig = configs.root();
		return props.getMaxConcurrentReplication() != null 
			? props.getMaxConcurrentReplication() 
			: serverConfig.getMaxConcurrentReplication();
	}
}
