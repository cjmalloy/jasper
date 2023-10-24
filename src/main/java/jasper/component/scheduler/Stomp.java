package jasper.component.scheduler;

import jasper.component.Notifications;
import jasper.config.Props;
import jasper.repository.RefRepository;
import jasper.service.dto.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("!no-websocket")
@Component
public class Stomp {

	@Autowired
	Props props;

	@Autowired
	Notifications notifications;

	@Autowired
	RefRepository refRepository;

	@Autowired
	SimpMessagingTemplate stomp;

	@Autowired
	DtoMapper mapper;

	private Instant lastModified = Instant.now();
	private boolean refs = false;

	@PostConstruct
	void init() {
		notifications.allRefListener(() -> refs = true);
	}

	@Scheduled(fixedDelay = 1000)
	public void send() {
		if (!refs) return;
		refs = false;
		var maybeRef = refRepository.findAllByModifiedAfterOrderByModifiedAsc(lastModified, PageRequest.of(0, props.getWebsocketBatchSize()));
		if (maybeRef.isEmpty()) return;
		for (var ref : maybeRef) {
			lastModified = ref.getModified();
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
	}
}
