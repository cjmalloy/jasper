package jasper.component;

import jasper.domain.Ref;
import jasper.service.dto.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("!no-websocket")
@Component
public class MessagesImplStomp implements Messages {

	@Autowired
	SimpMessagingTemplate stomp;

	@Autowired
	DtoMapper mapper;

	public void updateRef(Ref ref) {
		// TODO: Debounce
		var encodedUrl = URLEncoder.encode(ref.getUrl(), StandardCharsets.UTF_8);
		stomp.convertAndSend("/topic/ref/" + (isNotBlank(ref.getOrigin()) ? ref.getOrigin() : "default") + "/" + encodedUrl, mapper.domainToUpdateDto(ref));
		if (ref.getTags() != null)
		for (var tag : ref.getTags()) {
			var encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8);
			stomp.convertAndSend("/topic/tag/" + encodedTag, tag);
		}
		if (ref.getSources() != null)
		for (var source : ref.getSources()) {
			var encodedSource = URLEncoder.encode(source, StandardCharsets.UTF_8);
			stomp.convertAndSend("/topic/response/" + encodedSource, ref.getUrl());
		}
	}
}
