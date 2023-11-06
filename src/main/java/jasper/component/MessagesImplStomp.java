package jasper.component;

import jasper.domain.Ref;
import jasper.service.dto.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SubscriptionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("!no-websocket")
@Component
public class MessagesImplStomp implements Messages {

	@Autowired
	SubscriptionRegistry subscriptionRegistry;

	@Autowired
	SimpMessagingTemplate stomp;

	@Autowired
	DtoMapper mapper;

	private Map<String, List<Message<byte[]>>> sessions = new HashMap<>();

	@EventListener
	public void handleWebSocketConnectListener(SessionSubscribeEvent event) {
		try {
			sessions
				.computeIfAbsent(event.getUser().getName(), k -> new ArrayList<>())
				.add(event.getMessage());
		} catch (Exception e) {
			subscriptionRegistry.unregisterSubscription(event.getMessage());
		}
	}

	public void clear(Ref ref, Ref existing) {

	}

	public void updateRef(Ref ref, Ref existing) {
		// TODO: Debounce
		stomp.convertAndSend("/topic/ref/" + (isNotBlank(ref.getOrigin()) ? ref.getOrigin() : "default") + "/" + ref.getUrl(), mapper.domainToUpdateDto(ref));
		if (ref.getTags() != null)
		for (var tag : ref.getTags()) {
			// TODO: workaround +_ in destination
			stomp.convertAndSend("/topic/tag/" + tag.replace('_', '>').replace('+', '<'), tag);
		}
		if (ref.getSources() != null)
		for (var source : ref.getSources()) {
			stomp.convertAndSend("/topic/response/" + source, ref.getUrl());
		}
	}

	public void disconnectUser(String username) {
		if (sessions.containsKey(username)) {
			for (var m : sessions.get(username)) subscriptionRegistry.unregisterSubscription(m);
		}
	}
}
