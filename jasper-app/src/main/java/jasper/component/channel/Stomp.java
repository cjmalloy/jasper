package jasper.component.channel;

import jasper.component.dto.ComponentDtoMapper;
import jasper.domain.proj.HasOrigin;
import jasper.service.dto.ExtDto;
import jasper.service.dto.RefDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.domain.proj.HasOrigin.originHierarchy;
import static jasper.domain.proj.HasTags.formatTag;

@Profile("!no-websocket")
@Component
public class Stomp {

	@Autowired
	SimpMessagingTemplate stomp;

	@Autowired
	ComponentDtoMapper mapper;

	@Order(0)
	@ServiceActivator(inputChannel = "cursorRxChannel")
	public void handleCursorUpdate(Message<String> message) {
		stomp.convertAndSend("/topic/cursor/" + formatOrigin(message.getHeaders().get("origin")), message.getPayload());
	}

	@Order(0)
	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		var updateDto = mapper.dtoToUpdateDto(message.getPayload());
		var origin = HasOrigin.origin(message.getHeaders().get("origin").toString());
		var origins = originHierarchy(origin);
		for (var o : origins) {
			stomp.convertAndSend("/topic/ref/" + formatOrigin(o) + "/" + e(message.getHeaders().get("url")), updateDto);
		}
	}

	@Order(0)
	@ServiceActivator(inputChannel = "tagRxChannel")
	public void handleTagUpdate(Message<String> message) {
		var origin = HasOrigin.origin(message.getHeaders().get("origin").toString());
		var path = message.getHeaders().get("tag").toString();
		var tag = message.getPayload() + origin;
		var origins = originHierarchy(origin);
		for (var o : origins) {
			stomp.convertAndSend("/topic/tag/" + formatOrigin(o) + "/" + e(formatTag(path)), tag);
		}
	}

	@Order(0)
	@ServiceActivator(inputChannel = "responseRxChannel")
	public void handleResponseUpdate(Message<String> message) {
		var origin = HasOrigin.origin(message.getHeaders().get("origin").toString());
		var origins = originHierarchy(origin);
		for (var o : origins) {
			stomp.convertAndSend("/topic/response/" + formatOrigin(o) + "/" + e(message.getHeaders().get("response")), message.getPayload());
		}
	}

	@Order(0)
	@ServiceActivator(inputChannel = "extRxChannel")
	public void handleExtUpdate(Message<ExtDto> message) {
		var origin = HasOrigin.origin(message.getHeaders().get("origin").toString());
		var origins = originHierarchy(origin);
		for (var o : origins) {
			stomp.convertAndSend("/topic/ext/" + formatOrigin(o) + "/" + e(message.getHeaders().get("tag")), message.getPayload());
		}
	}

	private String e(Object o) {
		if (o == null) return "";
		return URLEncoder.encode((String) o, StandardCharsets.UTF_8);
	}
}
