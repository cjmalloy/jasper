package jasper.component;

import jasper.component.dto.ComponentDtoMapper;
import jasper.domain.Ref;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static jasper.domain.proj.HasOrigin.originHierarchy;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("!no-websocket")
@Component
public class MessagesImplStomp implements Messages {

	@Autowired
	SimpMessagingTemplate stomp;

	@Autowired
	ComponentDtoMapper mapper;

	public void updateRef(Ref ref) {
		// TODO: Debounce
		var encodedUrl = URLEncoder.encode(ref.getUrl(), StandardCharsets.UTF_8);
		var origins = originHierarchy(ref.getOrigin());
		for (var o : origins) {
			var origin = isNotBlank(o) ? o : "default";
			stomp.convertAndSend("/topic/ref/" + origin + "/" + encodedUrl, mapper.domainToUpdateDto(ref));
		}
		if (ref.getTags() != null){
			ref.addHierarchicalTags();
			for (var tag : ref.getTags()) {
				var encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8);
				for (var o : origins) {
					var origin = isNotBlank(o) ? o : "default";
					stomp.convertAndSend("/topic/tag/" + origin + "/" + encodedTag, tag);
				}
			}
		}
		if (ref.getSources() != null)
		for (var source : ref.getSources()) {
			var encodedSource = URLEncoder.encode(source, StandardCharsets.UTF_8);
			for (var o : origins) {
				var origin = isNotBlank(o) ? o : "default";
				stomp.convertAndSend("/topic/response/" + origin + "/" + encodedSource, ref.getUrl());
			}
		}
	}
}
