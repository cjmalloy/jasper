package jasper.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

import java.util.concurrent.ExecutorService;


@Profile("!redis")
@Configuration
public class SingleNodeConfig {

	@Autowired
	ExecutorService integrationExecutor;

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

	@Autowired
	MessageChannel bulkheadTxChannel;

	@Autowired
	MessageChannel bulkheadRxChannel;

	@Bean
	public IntegrationFlow directCursorFlow() {
		return IntegrationFlow
			.from(cursorTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(cursorRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directRefFlow() {
		return IntegrationFlow
			.from(refTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(refRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directTagFlow() {
		return IntegrationFlow
			.from(tagTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(tagRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directResponseFlow() {
		return IntegrationFlow
			.from(responseTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(responseRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directUserFlow() {
		return IntegrationFlow
			.from(userTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(userRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directExtFlow() {
		return IntegrationFlow
			.from(extTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(extRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directPluginFlow() {
		return IntegrationFlow
			.from(pluginTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(pluginRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directTemplateFlow() {
		return IntegrationFlow
			.from(templateTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(templateRxChannel)
			.get();
	}

	@Bean
	public IntegrationFlow directBulkheadFlow() {
		return IntegrationFlow
			.from(bulkheadTxChannel)
			.channel(new ExecutorChannel(integrationExecutor))
			.channel(new QueueChannel(4))
			.channel(bulkheadRxChannel)
			.get();
	}
}
