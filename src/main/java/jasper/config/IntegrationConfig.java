package jasper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.MessageChannel;

@Configuration
@EnableIntegration
public class IntegrationConfig {

	@Bean
	public MessageChannel refTxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel refRxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel tagTxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel tagRxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel responseTxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel responseRxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel userTxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel userRxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel pluginTxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel pluginRxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel templateTxChannel() {
		return new PublishSubscribeChannel();
	}

	@Bean
	public MessageChannel templateRxChannel() {
		return new PublishSubscribeChannel();
	}
}
