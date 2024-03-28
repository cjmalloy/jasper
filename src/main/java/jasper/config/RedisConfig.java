package jasper.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.service.dto.PluginDto;
import jasper.service.dto.RefDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
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
	public IntegrationFlow redisPublishRefFlow() {
		return IntegrationFlows
			.from(refTxChannel)
			.handle(new CustomPublishingMessageHandler<RefDto>() {
				@Override
				protected String getTopic(Message<RefDto> message) {
					return "ref/" + message.getHeaders().get("origin") + "/" + message.getHeaders().get("url");
				}

				@Override
				protected byte[] getMessage(Message<RefDto> message) {
					try {
						return objectMapper.writeValueAsBytes(message.getPayload());
					} catch (JsonProcessingException e) {
						logger.error("Cannot serialize RefDto.");
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
				var parts = new String(message.getChannel(), StandardCharsets.UTF_8).split("/");
				var origin = parts[1];
				refRxChannel.send(MessageBuilder.createMessage(ref, refHeaders(origin, ref)));
			} catch (IOException e) {
				logger.error("Error parsing RefDto from redis.");
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
			var parts = new String(message.getChannel(), StandardCharsets.UTF_8).split("/");
			var origin = parts[1];
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
					return "response/" + message.getHeaders().get("origin") + "/" + message.getHeaders().get("response");
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
			var parts = new String(message.getChannel(), StandardCharsets.UTF_8).split("/");
			var origin = parts[1];
			var source = parts[2];
			responseRxChannel.send(MessageBuilder.createMessage(response, responseHeaders(origin, source)));
		}, of("response/*"));
		return container;
	}

	@Bean
	public IntegrationFlow redisPublishUserFlow() {
		return IntegrationFlows
			.from(userTxChannel)
			.handle(new CustomPublishingMessageHandler<UserDto>() {
				@Override
				protected String getTopic(Message<UserDto> message) {
					return "user/" + message.getHeaders().get("origin") + "/" + message.getHeaders().get("tag");
				}

				@Override
				protected byte[] getMessage(Message<UserDto> message) {
					try {
						return objectMapper.writeValueAsBytes(message.getPayload());
					} catch (JsonProcessingException e) {
						logger.error("Cannot serialize UserDto.");
						throw new RuntimeException(e);
					}
				}
			})
			.get();
	}

	@Bean
	public RedisMessageListenerContainer redisUserRxAdapter(RedisConnectionFactory redisConnectionFactory) {
		var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener((message, pattern) -> {
			try {
				var user = objectMapper.readValue(message.getBody(), UserDto.class);
				var parts = new String(message.getChannel(), StandardCharsets.UTF_8).split("/");
				var origin = parts[1];
				var tag = parts[2];
				userRxChannel.send(MessageBuilder.createMessage(user, tagHeaders(origin, tag)));
			} catch (IOException e) {
				logger.error("Error parsing UserDto from redis.");
			}
		}, of("user/*"));
		return container;
	}

	@Bean
	public IntegrationFlow redisPublishPluginFlow() {
		return IntegrationFlows
			.from(pluginTxChannel)
			.handle(new CustomPublishingMessageHandler<PluginDto>() {
				@Override
				protected String getTopic(Message<PluginDto> message) {
					return "plugin/" + message.getHeaders().get("origin") + "/" + message.getHeaders().get("tag");
				}

				@Override
				protected byte[] getMessage(Message<PluginDto> message) {
					try {
						return objectMapper.writeValueAsBytes(message.getPayload());
					} catch (JsonProcessingException e) {
						logger.error("Cannot serialize PluginDto.");
						throw new RuntimeException(e);
					}
				}
			})
			.get();
	}

	@Bean
	public RedisMessageListenerContainer redisPluginRxAdapter(RedisConnectionFactory redisConnectionFactory) {
		var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener((message, pattern) -> {
			try {
				var plugin = objectMapper.readValue(message.getBody(), PluginDto.class);
				var parts = new String(message.getChannel(), StandardCharsets.UTF_8).split("/");
				var origin = parts[1];
				var tag = parts[2];
				pluginRxChannel.send(MessageBuilder.createMessage(plugin, tagHeaders(origin, tag)));
			} catch (IOException e) {
				logger.error("Error parsing PluginDto from redis.");
			}
		}, of("plugin/*"));
		return container;
	}

	@Bean
	public IntegrationFlow redisPublishTemplateFlow() {
		return IntegrationFlows
			.from(templateTxChannel)
			.handle(new CustomPublishingMessageHandler<TemplateDto>() {
				@Override
				protected String getTopic(Message<TemplateDto> message) {
					return "template/" + message.getHeaders().get("origin") + "/" + message.getHeaders().get("tag");
				}

				@Override
				protected byte[] getMessage(Message<TemplateDto> message) {
					try {
						return objectMapper.writeValueAsBytes(message.getPayload());
					} catch (JsonProcessingException e) {
						logger.error("Cannot serialize PluginDto.");
						throw new RuntimeException(e);
					}
				}
			})
			.get();
	}

	@Bean
	public RedisMessageListenerContainer redisTemplateRxAdapter(RedisConnectionFactory redisConnectionFactory) {
		var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(redisConnectionFactory);
		container.addMessageListener((message, pattern) -> {
			try {
				var template = objectMapper.readValue(message.getBody(), TemplateDto.class);
				var parts = new String(message.getChannel(), StandardCharsets.UTF_8).split("/");
				var origin = parts[1];
				var tag = parts[2];
				templateRxChannel.send(MessageBuilder.createMessage(template, tagHeaders(origin, tag)));
			} catch (IOException e) {
				logger.error("Error parsing TemplateDto from redis.");
			}
		}, of("plugin/*"));
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
