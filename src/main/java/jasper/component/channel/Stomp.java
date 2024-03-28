package jasper.component.channel;

import jasper.component.dto.ComponentDtoMapper;
import jasper.service.dto.RefDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class Stomp {

	@Autowired
	SimpMessagingTemplate stomp;

	@Autowired
	ComponentDtoMapper mapper;

	@Order(0)
	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		var updateDto = mapper.dtoToUpdateDto(message.getPayload());
		stomp.convertAndSend("/topic/ref/" + message.getHeaders().get("origin") + "/" + e(message.getHeaders().get("url")), updateDto);
	}

	@Order(0)
	@ServiceActivator(inputChannel = "tagRxChannel")
	public void handleTagUpdate(Message<String> message) {
		stomp.convertAndSend("/topic/tag/" + message.getHeaders().get("origin") + "/" + e(message.getHeaders().get("tag")), message.getPayload());
	}

	@Order(0)
	@ServiceActivator(inputChannel = "responseRxChannel")
	public void handleResponseUpdate(Message<String> message) {
		stomp.convertAndSend("/topic/response/" + message.getHeaders().get("origin") + "/" + e(message.getHeaders().get("response")), message.getPayload());
	}

	private String e(Object o) {
		if (o == null) return "";
		return URLEncoder.encode((String) o, StandardCharsets.UTF_8);
	}
}
