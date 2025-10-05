package jasper.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import jasper.component.ConfigCache;
import jasper.service.dto.BulkheadDto;
import jasper.service.dto.TemplateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static jasper.component.Messages.originHeaders;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.messaging.support.MessageBuilder.createMessage;

@Configuration
public class BulkheadConfiguration {
	private static final Logger logger = LoggerFactory.getLogger(BulkheadConfiguration.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	BulkheadRegistry registry;

	@Autowired
	MessageChannel bulkheadTxChannel;

	@Bean
	public Bulkhead httpBulkhead() {
		return registry.bulkhead("http", BulkheadConfig.custom()
			.maxConcurrentCalls(configs.root().getMaxConcurrentRequests())
			.maxWaitDuration(ofSeconds(0))
			.build());
	}

	@Bean
	public Bulkhead scriptBulkhead() {
		return registry.bulkhead("script", BulkheadConfig.custom()
			.maxConcurrentCalls(configs.root().getMaxConcurrentScripts())
			.maxWaitDuration(ofSeconds(60))
			.build());
	}

	@Bean
	public Bulkhead replBulkhead() {
		return registry.bulkhead("repl", BulkheadConfig.custom()
			.maxConcurrentCalls(configs.root().getMaxConcurrentReplication())
			.maxWaitDuration(ofMinutes(15))
			.build());
	}

	@Bean
	public Bulkhead fetchBulkhead() {
		return registry.bulkhead("fetch", BulkheadConfig.custom()
			.maxConcurrentCalls(configs.root().getMaxConcurrentFetch())
			.maxWaitDuration(ofMinutes(5))
			.build());
	}

	@Bean
	public Bulkhead recyclerBulkhead() {
		return registry.bulkhead("recycler", BulkheadConfig.custom()
			.maxConcurrentCalls(1)
			.maxWaitDuration(ofMinutes(0))
			.build());
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		if (isBlank(template.getTag())) return;
		if (template.getTag().startsWith("_config/server")) {
			var origin = (String) message.getHeaders().get("origin");
			logger.debug("{} Server config template updated, updating bulkhead configurations", origin);
			updateBulkheadConfig(httpBulkhead(), configs.root().getMaxConcurrentRequests(), origin);
			updateBulkheadConfig(scriptBulkhead(), configs.root().getMaxConcurrentScripts(), origin);
			updateBulkheadConfig(replBulkhead(), configs.root().getMaxConcurrentReplication(), origin);
			updateBulkheadConfig(fetchBulkhead(), configs.root().getMaxConcurrentFetch(), origin);
		}
	}

	@ServiceActivator(inputChannel = "bulkheadRxChannel")
	public void handleBulkheadUpdate(Message<BulkheadDto> message) {
		var bulkhead = message.getPayload();
		var origin = (String) message.getHeaders().get("origin");
		logger.info("{} Bulkhead {} updated to {} max concurrent calls", origin, bulkhead.getName(), bulkhead.getMaxConcurrentCalls());
	}

	public void updateBulkheadConfig(Bulkhead bulkhead, int limit, String origin) {
		logger.debug("{} Creating bulkhead with {} permits", origin, limit);
		updateBulkheadConfig(bulkhead, limit);
		// Publish bulkhead state to Redis for distributed visibility
		var dto = BulkheadDto.builder()
			.name(bulkhead.getName())
			.maxConcurrentCalls(limit)
			.build();
		bulkheadTxChannel.send(createMessage(dto, originHeaders(origin)));
	}

	public static void updateBulkheadConfig(Bulkhead bulkhead, int limit) {
		bulkhead.changeConfig(BulkheadConfig
			.from(bulkhead.getBulkheadConfig())
			.maxConcurrentCalls(limit)
			.build());
	}
}
