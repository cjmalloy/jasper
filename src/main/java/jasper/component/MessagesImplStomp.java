package jasper.component;

import jasper.domain.Ref;
import jasper.service.dto.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("!no-websocket")
@Component
public class MessagesImplStomp {

	@Autowired
	SimpMessagingTemplate stomp;

	@Autowired
	DtoMapper mapper;

	public void updateRef(Ref ref) {
		// TODO: Debounce
		stomp.convertAndSend("/topic/ref/" + (isNotBlank(ref.getOrigin()) ? ref.getOrigin() : "default") + "/" + ref.getUrl(), mapper.domainToDto(ref));
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
}
