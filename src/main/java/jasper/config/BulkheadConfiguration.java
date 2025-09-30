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

import static java.time.Duration.ofSeconds;

@Configuration
public class BulkheadConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(BulkheadConfiguration.class);

	@Autowired
	ConfigCache configs;

	@Bean
	public Bulkhead httpBulkhead() {
		return Bulkhead.of("http-global", BulkheadConfig.custom()
			.maxConcurrentCalls(configs.root().getMaxConcurrentRequests())
			.maxWaitDuration(ofSeconds(0))
			.build());
	}

	@Bean
	public Bulkhead scriptBulkhead() {
		return Bulkhead.of("global-script-execution", BulkheadConfig.custom()
			.maxConcurrentCalls(configs.root().getMaxConcurrentScripts())
			.maxWaitDuration(ofSeconds(60))
			.build());
	}

	@Bean
	public Bulkhead replBulkhead() {
		return Bulkhead.of("global-replication", BulkheadConfig.custom()
			.maxConcurrentCalls(configs.root().getMaxConcurrentReplication())
			.maxWaitDuration(ofSeconds(30))
			.build());
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<Template> templateMessage) {
		var template = templateMessage.getPayload();
		if (template.getTag() != null && template.getTag().startsWith("_config/server")) {
			logger.debug("Server config template updated, updating bulkhead configurations");
			updateBulkheadConfig(httpBulkhead(), configs.root().getMaxConcurrentRequests());
			updateBulkheadConfig(scriptBulkhead(), configs.root().getMaxConcurrentScripts());
			updateBulkheadConfig(replBulkhead(), configs.root().getMaxConcurrentReplication());
		}
	}

	private void updateBulkheadConfig(Bulkhead bulkhead, int limit) {
		var newConfig = BulkheadConfig.custom()
			.maxConcurrentCalls(limit)
			.maxWaitDuration(bulkhead.getBulkheadConfig().getMaxWaitDuration())
			.build();
		bulkhead.changeConfig(newConfig);
	}
}
