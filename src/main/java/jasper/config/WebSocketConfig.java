package jasper.config;

import jasper.security.Auth;
import jasper.security.jwt.TokenProvider;
import jasper.security.jwt.TokenProviderImplDefault;
import org.apache.tomcat.websocket.server.WsSci;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("!no-websocket")
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

	public static final String AUTHORIZATION_HEADER = "Authorization";

	@Autowired
	Props props;

	@Autowired
	TokenProvider tokenProvider;

	@Autowired
	TokenProviderImplDefault defaultTokenProvider;

	@Autowired
	@Qualifier("authSingleton")
	Auth auth;

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
			.setHandshakeHandler(new StompDefaultHandshakeHandler())
			.addInterceptors(new StompHandshakeInterceptor())
				.setAllowedOriginPatterns("*")
				.withSockJS()
					.setSuppressCors(true);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new JwtChannelInterceptor());
	}

	class StompHandshakeInterceptor implements HandshakeInterceptor {

		@Override
		public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
			if (request instanceof ServletServerHttpRequest servletRequest) {
				var httpServletRequest = servletRequest.getServletRequest();
				var token = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
				if (isNotBlank(token)) {
					attributes.put("jwt", token.substring("Bearer ".length()));
				}
			}
			return true;
		}

		@Override
		public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
		}
	}

	class StompDefaultHandshakeHandler extends DefaultHandshakeHandler {
		@Override
		public Principal determineUser(ServerHttpRequest request, WebSocketHandler handler, Map<String, Object> attributes) {
			logger.debug("STOMP Request Principal: " + request.getPrincipal());
			var token = (String) attributes.get("jwt");
			TokenProvider t = tokenProvider.validateToken(token) ? tokenProvider : defaultTokenProvider;
			return t.getAuthentication(token);
		}
	}

	class JwtChannelInterceptor implements ChannelInterceptor {
		@Override
		public Message<?> preSend(Message<?> message, MessageChannel channel) {
			try {
				var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				if (accessor.getCommand() == StompCommand.BEGIN) return null; // No Transactions
				if (accessor.getCommand() == StompCommand.SEND) return null; // No Client Messages
				if (accessor.getCommand() != StompCommand.SUBSCRIBE) return message;
				var headers = message.getHeaders().get("nativeHeaders", Map.class);
				var token = ((ArrayList<String>) headers.get("jwt")).get(0);
				if  (isNotBlank(token)) {
					logger.debug("STOMP SUBSCRIBE Credentials Header");
					TokenProvider t = tokenProvider.validateToken(token) ? tokenProvider : defaultTokenProvider;
					auth.clear(t.getAuthentication(token));
				} else if (accessor.getUser() instanceof Authentication authentication) {
					logger.debug("WS User Set");
					auth.clear(authentication);
				} else {
					throw new AccessDeniedException("No auth token");
				}
				if (auth.canSubscribeTo(accessor.getDestination())) return message;
				logger.error("{} can't subscribe to {}", auth.getUserTag(), accessor.getDestination());
				logger.debug("read access: {}", auth.getReadAccess());
				logger.debug("tenant access: {}", Arrays.toString(props.getSecurity().getClient(auth.getOrigin()).getTenantAccess()));
			} catch (Exception e) {
				logger.warn("Cannot authorize websocket subscription.");
			}
			logger.error("Websocket authentication failed.");
			return null;
		}
	}
}
