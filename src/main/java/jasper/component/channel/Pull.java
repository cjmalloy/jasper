package jasper.component.channel;

import jakarta.annotation.PostConstruct;
import jakarta.websocket.DeploymentException;
import jasper.component.ConfigCache;
import jasper.component.Replicator;
import jasper.component.TunnelClient;
import jasper.config.Props;
import jasper.domain.proj.HasTags;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.domain.proj.HasTags.hasMatchingTag;
import static jasper.plugin.Origin.getOrigin;
import static jasper.plugin.Pull.getPull;

@Component
public class Pull {
	private static final Logger logger = LoggerFactory.getLogger(Pull.class);

	@Autowired
	Props props;

	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Replicator replicator;

	@Autowired
	TunnelClient tunnelClient;

	@Autowired
	Watch watch;

	@Autowired
	@Qualifier("websocketExecutor")
	private Executor websocketExecutor;

	record MonitorInfo(String url, String origin, WebSocketStompClient client) {}
	private Map<String, Instant> lastSent = new ConcurrentHashMap<>();
	private Map<String, Instant> queued = new ConcurrentHashMap<>();
	private Map<String, MonitorInfo> pulls = new ConcurrentHashMap<>();

	@PostConstruct
	public void init() {
		for (var origin : configs.root().getPullWebsocketOrigins()) {
			watch.addWatch(origin, "+plugin/origin/pull", this::watch);
		}
	}

	private void watch(HasTags update) {
		var remote = refRepository.findOneByUrlAndOrigin(update.getUrl(), update.getOrigin()).orElse(null);
		var config = getOrigin(remote);
		var pull = getPull(remote);
		if (remote == null || config == null || pull == null) {
			for (var e : pulls.entrySet()) {
				if (e.getValue().url.equals(update.getUrl()) && e.getValue().origin.equals(update.getOrigin())) {
					logger.info("{} Disconnecting origin ({}) from websocket {}: {}", update.getOrigin(), e.getKey(), update.getTitle(), update.getUrl());
					pulls.remove(e.getKey());
					e.getValue().client.stop();
					return;
				}
			}
			return;
		}
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		var remoteOrigin = origin(config.getRemote());
		pulls.compute(localOrigin, (o, info) -> {
			if (hasMatchingTag(update, "plugin/delete") || remote.hasTag("+plugin/error") || !remote.hasTag("+plugin/cron") || !pull.isWebsocket()) {
				if (info != null) {
					logger.info("{} Disconnecting origin ({}) from websocket {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
					info.client.stop();
					tunnelClient.releaseProxy(remote);
				}
				return null;
			}
			if (info != null) {
				if (info.client.isRunning()) return info;
				logger.error("{} Restarting monitor ({}) from websocket {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
				tunnelClient.releaseProxy(remote);
			}
			URI url;
			try {
				url = tunnelClient.reserveProxy(remote);
			} catch (Exception e) {
				logger.info("{} Error connecting to ({}) via ssh {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
				return null;
			}
			logger.info("{} Monitoring origin ({}) via websocket {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
			var stomp = getWebSocketStompClient();
			stomp.setDefaultHeartbeat(new long[]{10000, 10000});
			stomp.setTaskScheduler(taskScheduler);
			var future = stomp.connectAsync(url.resolve("/api/stomp/").toString(), new StompSessionHandlerAdapter() {
				@Override
				public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
					session.subscribe("/topic/cursor/" + formatOrigin(remoteOrigin), new StompFrameHandler() {
						@Override
						public Type getPayloadType(StompHeaders headers) {
							return Instant.class;
						}

						@Override
						public void handleFrame(StompHeaders headers, Object payload) {
							handleCursorUpdate(remote.getOrigin(), localOrigin, (Instant) payload);
						}
					});
				}

				@Override
				public void handleTransportError(StompSession session, Throwable exception) {
					logger.debug("Websocket Client Transport error");
					// Will automatically reconnect due to SockJS
				}

				@Override
				public void handleException(StompSession session, StompCommand command,
											StompHeaders headers, byte[] payload, Throwable exception) {
					logger.error("Error in websocket connection", exception);
					// Will automatically reconnect due to SockJS
				}
			});
			CompletableFuture.runAsync(() -> future.thenAcceptAsync(session -> {
				// TODO: add plugin response to origin to show connection status
				logger.info("{} Connected to ({}) via websocket {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
			})
			.exceptionally(e -> {
				logger.error("Error creating websocket session", e);
				stomp.stop();
				tunnelClient.killProxy(remote);
				if (e instanceof DeploymentException) return null;
				taskScheduler.schedule(() -> watch(update), Instant.now().plus(props.getPullWebsocketCooldownSec(), ChronoUnit.SECONDS));
				return null;
			}), websocketExecutor);
			return new MonitorInfo(remote.getUrl(), remote.getOrigin(), stomp);
		});
	}

	private void handleCursorUpdate(String origin, String local, Instant cursor) {
		if (!configs.root().getPullWebsocketOrigins().contains(origin)) return;
		if (cursor.equals(lastSent.computeIfAbsent(local, o -> cursor))) {
			taskScheduler.schedule(() -> pull(local), Instant.now());
		} else {
			queued.put(local, cursor);
		}
	}

	private void pull(String local) {
		try {
			pulls.compute(local, (k, info) -> {
				if (info == null) return null;
				var maybeRemote = refRepository.findOneByUrlAndOrigin(info.url, info.origin);
				if (maybeRemote.isPresent()) {
					var remote = maybeRemote.get();
					logger.debug("{} Pulling origin ({}) on websocket {}: {}", remote.getOrigin(), formatOrigin(local), remote.getTitle(), remote.getUrl());
					replicator.pull(remote);
					logger.debug("{} Finished pulling origin ({}) on websocket {}: {}", remote.getOrigin(), formatOrigin(local), remote.getTitle(), remote.getUrl());
					return info;
				}
				return null;
			});
		} finally {
			taskScheduler.schedule(() -> checkIfQueued(local), Instant.now());
		}
	}

	private void checkIfQueued(String local) {
		if (!lastSent.containsKey(local)) return;
		var next = queued.remove(local);
		if (next != null) {
			lastSent.put(local, next);
			pull(local);
		} else {
			lastSent.remove(local);
		}
	}

	private static WebSocketStompClient getWebSocketStompClient() {
		var stomp = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
		stomp.setMessageConverter(new MessageConverter() {
			@Override
			public Object fromMessage(Message<?> message, Class<?> targetClass) {
				return Instant.parse(new String((byte[]) message.getPayload(), StandardCharsets.UTF_8));
			}

			@Override
			public Message<?> toMessage(Object payload, MessageHeaders headers) {
				return MessageBuilder.createMessage(payload, headers);
			}
		});
		return stomp;
	}
}
