package jasper.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.dto.RefUpdateDto;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static jasper.component.Messages.refHeaders;
import static jasper.component.Messages.responseHeaders;
import static jasper.component.Messages.tagHeaders;
import static org.springframework.data.redis.listener.PatternTopic.of;

@Profile("redis")
@Import(RedisAutoConfiguration.class)
@Configuration
public class RedisConfig {
	private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RedisConnectionFactory redisConnectionFactory;

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

	@Bean
	public IntegrationFlow redisPublishRefFlow() {
		return IntegrationFlows
			.from(refTxChannel)
			.handle(new CustomPublishingMessageHandler<RefUpdateDto>() {
				@Override
				protected String getTopic(Message<RefUpdateDto> message) {
					return "ref/" + message.getHeaders().get("origin") + "/" + message.getHeaders().get("url");
				}

				@Override
				protected byte[] getMessage(Message<RefUpdateDto> message) {
					try {
						return objectMapper.writeValueAsBytes(message.getPayload());
					} catch (JsonProcessingException e) {
						logger.error("Cannot serialize RefUpdateDto.");
						throw new RuntimeException(e);
					}
				}
			})
			.get();
	}

	@Bean
	public RedisMessageListenerContainer redisRefRxAdapter(RedisConnectionFactory redisConnectionFactory) {
		var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener((message, pattern) -> {
			try {
				var ref = objectMapper.readValue(message.getBody(), RefDto.class);
				var origin = new String(message.getChannel(), StandardCharsets.UTF_8).split("/")[1];
				refRxChannel.send(MessageBuilder.createMessage(ref, refHeaders(origin, ref)));
			} catch (IOException e) {
				logger.error("Error parsing RefUpdateDto from redis.");
			}
		}, of("ref/*"));
		return container;
	}

	@Bean
	public IntegrationFlow redisPublishTagFlow() {
		return IntegrationFlows
			.from(tagTxChannel)
			.handle(new CustomPublishingMessageHandler<String>() {
				@Override
				protected String getTopic(Message<String> message) {
					return "tag/" + message.getHeaders().get("tag");
				}

				@Override
				protected byte[] getMessage(Message<String> message) {
					return message.getPayload().getBytes();
				}
			})
			.get();
	}

	@Bean
	public RedisMessageListenerContainer redisTagRxAdapter(RedisConnectionFactory redisConnectionFactory) {
		var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener((message, pattern) -> {
			var tag = new String(message.getBody(), StandardCharsets.UTF_8);
			var origin = new String(message.getChannel(), StandardCharsets.UTF_8).split("/")[1];
			tagRxChannel.send(MessageBuilder.createMessage(tag, tagHeaders(origin, tag)));
		}, of("tag/*"));
		return container;
	}

	@Bean
	public IntegrationFlow redisPublishResponseFlow() {
		return IntegrationFlows
			.from(responseTxChannel)
			.handle(new CustomPublishingMessageHandler<String>() {
				@Override
				protected String getTopic(Message<String> message) {
					return "response/" + message.getHeaders().get("response");
				}

				@Override
				protected byte[] getMessage(Message<String> message) {
					return message.getPayload().getBytes();
				}
			})
			.get();
	}

	@Bean
	public RedisMessageListenerContainer redisResponseRxAdapter(RedisConnectionFactory redisConnectionFactory) {
		var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener((message, pattern) -> {
			var response = new String(message.getBody(), StandardCharsets.UTF_8);
			var origin = new String(message.getChannel(), StandardCharsets.UTF_8).split("/")[1];
			var source = new String(message.getChannel(), StandardCharsets.UTF_8).split("/")[2];
			responseRxChannel.send(MessageBuilder.createMessage(response, responseHeaders(origin, source)));
		}, of("response/*"));
		return container;
	}

	private abstract class CustomPublishingMessageHandler<T> extends AbstractMessageHandler {

		private final RedisTemplate<?, ?> template;

		public CustomPublishingMessageHandler() {
			this.template = new RedisTemplate<>();
			this.template.setConnectionFactory(redisConnectionFactory);
			this.template.setEnableDefaultSerializer(false);
			this.template.afterPropertiesSet();
		}

		@Override
		public String getComponentType() {
			return "redis:outbound-channel-adapter";
		}

		@Override
		@SuppressWarnings("unchecked")
		protected void handleMessageInternal(Message<?> message) {
			this.template.convertAndSend(getTopic((Message<T>) message), getMessage((Message<T>) message));
		}

		protected abstract String getTopic(Message<T> message);
		protected abstract byte[] getMessage(Message<T> message);
	}
}
