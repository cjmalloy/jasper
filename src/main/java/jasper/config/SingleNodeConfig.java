package jasper.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.MessageChannel;

@Profile("!redis")
@Configuration
public class SingleNodeConfig {

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
	MessageChannel pluginTxChannel;

	@Autowired
	MessageChannel pluginRxChannel;

	@Autowired
	MessageChannel templateTxChannel;

	@Autowired
	MessageChannel templateRxChannel;

	@Bean
	public IntegrationFlow directRefFlow() {
		return IntegrationFlows.from(refTxChannel)
							   .channel(refRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directTagFlow() {
		return IntegrationFlows.from(tagTxChannel)
							   .channel(tagRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directResponseFlow() {
		return IntegrationFlows.from(responseTxChannel)
							   .channel(responseRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directUserFlow() {
		return IntegrationFlows.from(userTxChannel)
							   .channel(responseRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directPluginFlow() {
		return IntegrationFlows.from(pluginTxChannel)
							   .channel(pluginRxChannel)
							   .get();
	}

	@Bean
	public IntegrationFlow directTemplateFlow() {
		return IntegrationFlows.from(templateTxChannel)
							   .channel(templateRxChannel)
							   .get();
	}
}
