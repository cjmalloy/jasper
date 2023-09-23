package jasper.config;

import jasper.security.jwt.TokenProvider;
import jasper.security.jwt.TokenProviderImplDefault;
import org.apache.tomcat.websocket.server.WsSci;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

	@Autowired
	TokenProvider tokenProvider;

	@Autowired
	TokenProviderImplDefault defaultTokenProvider;

//	@Autowired
//	@Qualifier("authSingleton")
//	Auth auth;

	@Bean
	public TomcatServletWebServerFactory tomcatContainerFactory() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();;
		factory.setTomcatContextCustomizers(Collections.singletonList(tomcatContextCustomizer()));
		return factory;
	}

	@Bean
	public TomcatContextCustomizer tomcatContextCustomizer() {
		return context -> context.addServletContainerInitializer(new WsSci(), null);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/topic");
		config.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry
			.addEndpoint("/")
				.setAllowedOriginPatterns("*")
				.withSockJS()
					.setSuppressCors(true);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new JwtChannelInterceptor());
	}

	class JwtChannelInterceptor implements ChannelInterceptor {
		@Override
		public Message<?> preSend(Message<?> message, MessageChannel channel) {
			try {
				var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				if (accessor.getCommand() == StompCommand.BEGIN) return null; // No Transactions
				if (accessor.getCommand() == StompCommand.SEND) return null; // No Client Messages
				if (accessor.getCommand() != StompCommand.SUBSCRIBE) return message;
				var jwt = ((ArrayList<String>) message.getHeaders().get("nativeHeaders", Map.class).get("jwt")).get(0);
				TokenProvider t = tokenProvider.validateToken(jwt) ? tokenProvider : defaultTokenProvider;
//				auth.clear(t.getAuthentication(jwt));
//				if (auth.canSubscribeTo(accessor.getDestination())) return message;
			} catch (Exception e) {
				logger.warn("Cannot authorize websocket subscription.");
			}
			return null;
		}
	}
}
