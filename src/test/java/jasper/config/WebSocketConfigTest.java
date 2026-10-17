package jasper.config;

import jasper.component.ConfigCache;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.security.AuthFactory;
import jasper.security.jwt.TokenProvider;
import jasper.security.jwt.TokenProviderImplDefault;
import jasper.service.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static jasper.component.Messages.tagHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {
	private WebSocketConfig config;
	private MessageChannel channel;

	@BeforeEach
	void setUp() {
		config = new WebSocketConfig();
		config.props = new Props();
		config.configs = mock(ConfigCache.class);
		config.tokenProvider = mock(TokenProvider.class);
		config.defaultTokenProvider = mock(TokenProviderImplDefault.class);
		config.authFactory = mock(AuthFactory.class);
		channel = mock(MessageChannel.class);
		when(config.configs.root()).thenReturn(new Config.ServerConfig());
	}

	@Test
	void authFactoryCreatesFreshAuthInstances() {
		var factory = new AuthFactory(new Props(), mock(RoleHierarchy.class), mock(ConfigCache.class), mock(RefRepository.class));

		assertThat(factory.create()).isNotSameAs(factory.create());
	}

	@Test
	void concurrentSessionsUseTheirOwnAuthentication() throws Exception {
		var barrier = new CyclicBarrier(2);
		var auths = new CopyOnWriteArrayList<TrackingAuth>();
		when(config.authFactory.create()).thenAnswer(invocation -> {
			var auth = new TrackingAuth(barrier);
			auths.add(auth);
			return auth;
		});
		var firstAuthentication = authentication("first");
		var secondAuthentication = authentication("second");
		var first = subscribe("/topic/first", firstAuthentication, null);
		var second = subscribe("/topic/second", secondAuthentication, null);

		assertThat(authorizeConcurrently(first, second)).containsExactly(first, second);
		assertThat(auths).hasSize(2).allSatisfy(auth -> assertThat(auth.authentications).hasSize(1));
		assertThat(auths.stream()
			.flatMap(auth -> auth.authentications.stream())
			.map(Authentication::getPrincipal))
			.containsExactlyInAnyOrder("first", "second");
	}

	@Test
	void concurrentSubscriptionsInOneSessionUseTheirOwnJwt() throws Exception {
		var barrier = new CyclicBarrier(2);
		var auths = new CopyOnWriteArrayList<TrackingAuth>();
		when(config.authFactory.create()).thenAnswer(invocation -> {
			var auth = new TrackingAuth(barrier);
			auths.add(auth);
			return auth;
		});
		var firstJwtAuthentication = authentication("first-jwt");
		var secondJwtAuthentication = authentication("second-jwt");
		when(config.tokenProvider.validateToken(anyString(), anyString())).thenReturn(true);
		when(config.tokenProvider.getAuthentication("first-token", "")).thenReturn(firstJwtAuthentication);
		when(config.tokenProvider.getAuthentication("second-token", "")).thenReturn(secondJwtAuthentication);
		var sessionAttributes = sessionAttributes();
		var first = subscribe("/topic/first-jwt", authentication("session"), "first-token", sessionAttributes);
		var second = subscribe("/topic/second-jwt", authentication("session"), "second-token", sessionAttributes);

		assertThat(authorizeConcurrently(first, second)).containsExactly(first, second);
		assertThat(auths).hasSize(2).allSatisfy(auth -> assertThat(auth.authentications).hasSize(2));
		assertThat(auths.stream().map(TrackingAuth::principals))
			.containsExactlyInAnyOrder(
				List.of("session", "first-jwt"),
				List.of("session", "second-jwt")
			);
	}

	@Test
	void subscriptionJwtIsIgnoredWithoutSessionAuthentication() {
		var auth = new TrackingAuth(new CyclicBarrier(1));
		when(config.authFactory.create()).thenReturn(auth);
		when(config.defaultTokenProvider.getAuthentication(null, "")).thenReturn(authentication("default"));
		var message = subscribe("/topic/default", null, "token");

		assertThat(config.new JwtChannelInterceptor().preSend(message, channel)).isSameAs(message);
		assertThat(auth.principals()).containsExactly("default");
		verifyNoInteractions(config.tokenProvider);
		verify(config.defaultTokenProvider).getAuthentication(null, "");
	}

	@Test
	void userUpdateOnlyClosesThatUsersSessions() throws Exception {
		when(config.authFactory.create()).thenAnswer(invocation -> new SubscribingAuth());
		var aliceAttributes = sessionAttributes();
		var bobAttributes = sessionAttributes();
		var interceptor = config.new JwtChannelInterceptor();
		assertThat(interceptor.preSend(subscribe("/topic/+user/alice", authentication("+user/alice"), null, aliceAttributes), channel)).isNotNull();
		assertThat(interceptor.preSend(subscribe("/topic/+user/bob", authentication("+user/bob"), null, bobAttributes), channel)).isNotNull();
		var alice = session(aliceAttributes);
		var bob = session(bobAttributes);
		var anonymous = session(sessionAttributes());
		config.sessions.addAll(List.of(alice, bob, anonymous));

		config.handleUserUpdate(MessageBuilder.createMessage(new UserDto(), tagHeaders("", "+user/alice")));

		verify(alice).close(CloseStatus.SERVICE_RESTARTED);
		verify(bob, never()).close(any());
		verify(anonymous, never()).close(any());
		assertThat(config.sessions).containsExactlyInAnyOrder(bob, anonymous);
	}

	@Test
	void userUpdateMatchesOrigin() throws Exception {
		var root = new Config.ServerConfig();
		root.setWebOrigins(List.of("", "@other"));
		when(config.configs.root()).thenReturn(root);
		when(config.authFactory.create()).thenAnswer(invocation -> new SubscribingAuth());
		var localAttributes = sessionAttributes();
		var remoteAttributes = sessionAttributes();
		var interceptor = config.new JwtChannelInterceptor();
		interceptor.preSend(subscribe("/topic/+user/alice", authentication("+user/alice"), null, localAttributes), channel);
		interceptor.preSend(subscribe("/topic/+user/alice@other", authentication("+user/alice@other"), null, remoteAttributes), channel);
		var local = session(localAttributes);
		var remote = session(remoteAttributes);
		config.sessions.addAll(List.of(local, remote));

		config.handleUserUpdate(MessageBuilder.createMessage(new UserDto(), tagHeaders("@other", "+user/alice")));

		verify(local, never()).close(any());
		verify(remote).close(CloseStatus.SERVICE_RESTARTED);
	}

	@Test
	void userUpdateClosesHandshakeUsersSessionWithoutSubscriptions() throws Exception {
		when(config.authFactory.create()).thenAnswer(invocation -> new SubscribingAuth());
		when(config.defaultTokenProvider.getAuthentication(null, "")).thenReturn(authentication("+user/alice"));
		var attributes = handshakeAttributes();
		config.new StompDefaultHandshakeHandler().determineUser(null, null, attributes);
		var alice = session(attributes);
		config.sessions.add(alice);

		config.handleUserUpdate(MessageBuilder.createMessage(new UserDto(), tagHeaders("", "+user/alice")));

		verify(alice).close(CloseStatus.SERVICE_RESTARTED);
		assertThat(config.sessions).isEmpty();
	}

	@Test
	void userUpdateDuringHandshakeClosesSession() throws Exception {
		when(config.authFactory.create()).thenAnswer(invocation -> new SubscribingAuth());
		when(config.defaultTokenProvider.getAuthentication(null, "")).thenReturn(authentication("+user/alice"));
		var registration = mock(WebSocketTransportRegistration.class);
		var factory = ArgumentCaptor.forClass(WebSocketHandlerDecoratorFactory.class);
		config.configureWebSocketTransport(registration);
		verify(registration).addDecoratorFactory(factory.capture());
		var handler = factory.getValue().decorate(mock(WebSocketHandler.class));
		var stale = handshakeAttributes();
		config.new StompDefaultHandshakeHandler().determineUser(null, null, stale);
		config.handleUserUpdate(MessageBuilder.createMessage(new UserDto(), tagHeaders("", "+user/alice")));
		var fresh = handshakeAttributes();
		config.new StompDefaultHandshakeHandler().determineUser(null, null, fresh);
		var staleSession = session(stale);
		var freshSession = session(fresh);

		handler.afterConnectionEstablished(staleSession);
		handler.afterConnectionEstablished(freshSession);

		verify(staleSession).close(CloseStatus.SERVICE_RESTARTED);
		verify(freshSession, never()).close(any());
		assertThat(config.sessions).containsExactly(freshSession);
	}

	@Test
	void userUpdateDuringSubscriptionReauthorizes() {
		var revoked = new AtomicBoolean();
		var auths = new AtomicInteger();
		when(config.authFactory.create()).thenAnswer(invocation -> {
			auths.incrementAndGet();
			return new SubscribingAuth() {
				@Override
				public boolean canSubscribeTo(String destination) {
					var allowed = !revoked.get();
					if (!revoked.getAndSet(true)) {
						config.handleUserUpdate(MessageBuilder.createMessage(new UserDto(), tagHeaders("", "+user/alice")));
					}
					return allowed;
				}
			};
		});
		var attributes = sessionAttributes();
		config.sessions.add(session(attributes));

		assertThat(config.new JwtChannelInterceptor().preSend(subscribe("/topic/+user/alice", authentication("+user/alice"), null, attributes), channel)).isNull();
		assertThat(auths).hasValue(2);
	}

	private HashMap<String, Object> handshakeAttributes() {
		var attributes = sessionAttributes();
		attributes.put("origin", "");
		return attributes;
	}

	private WebSocketSession session(Map<String, Object> attributes) {
		var session = mock(WebSocketSession.class);
		when(session.getAttributes()).thenReturn(attributes);
		return session;
	}

	private List<Message<?>> authorizeConcurrently(Message<?> first, Message<?> second) throws Exception {
		var interceptor = config.new JwtChannelInterceptor();
		try (var executor = Executors.newFixedThreadPool(2)) {
			var firstResult = executor.submit(() -> interceptor.preSend(first, channel));
			var secondResult = executor.submit(() -> interceptor.preSend(second, channel));
			return List.of(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
		}
	}

	private Message<byte[]> subscribe(String destination, Authentication authentication, String jwt) {
		return subscribe(destination, authentication, jwt, sessionAttributes());
	}

	private Message<byte[]> subscribe(String destination, Authentication authentication, String jwt, HashMap<String, Object> sessionAttributes) {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination(destination);
		accessor.setUser(authentication);
		accessor.setSessionAttributes(sessionAttributes);
		if (jwt != null) accessor.setNativeHeader("jwt", jwt);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private HashMap<String, Object> sessionAttributes() {
		var sessionAttributes = new HashMap<String, Object>();
		sessionAttributes.put("wsAttributes", new WebSocketConfig.WebSocketRequestAttributes());
		return sessionAttributes;
	}

	private Authentication authentication(String principal) {
		return new UsernamePasswordAuthenticationToken(principal, null, List.of());
	}

	private static class SubscribingAuth extends Auth {
		SubscribingAuth() {
			super(null, null, null, null);
		}

		@Override
		public boolean canSubscribeTo(String destination) {
			return true;
		}
	}

	private static class TrackingAuth extends Auth {
		private final CyclicBarrier barrier;
		private final List<Authentication> authentications = new ArrayList<>();

		TrackingAuth(CyclicBarrier barrier) {
			super(null, null, null, null);
			this.barrier = barrier;
		}

		@Override
		public void clear(Authentication authentication) {
			authentications.add(authentication);
		}

		@Override
		public String getOrigin() {
			return "";
		}

		@Override
		public boolean canSubscribeTo(String destination) {
			try {
				barrier.await(5, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			return destination.equals("/topic/" + authentications.getLast().getPrincipal());
		}

		List<Object> principals() {
			return authentications.stream().map(Authentication::getPrincipal).toList();
		}
	}
}
