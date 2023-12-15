package jasper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.MessageChannel;

@Configuration
@EnableIntegration
public class IntegrationConfig {

	@Bean
	public MessageChannel refTxChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel refRxChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel tagTxChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel tagRxChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel responseTxChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel responseRxChannel() {
		return new DirectChannel();
	}
}
