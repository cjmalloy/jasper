package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.component.Replicator;
import jasper.config.Props;
import jasper.domain.Ref_;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.domain.proj.HasOrigin.origin;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.domain.Sort.by;

@Component
public class Push {
	private static final Logger logger = LoggerFactory.getLogger(Push.class);

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

	private Map<String, String> lastSent = new ConcurrentHashMap<>();
	private Map<String, String> queued = new ConcurrentHashMap<>();

	@ServiceActivator(inputChannel = "cursorRxChannel")
	public void handleCursorUpdate(Message<String> message) {
		var root = configs.root();
		var origin = origin(message.getHeaders().get("origin").toString());
		if (!root.getPushOrigins().contains(origin)) return;
		var cursor = message.getPayload();
		if (cursor.equals(lastSent.putIfAbsent(origin, cursor))) {
			push(origin);
		} else {
			queued.put(origin, cursor);
		}
	}

	private void checkIfQueued(String origin) {
		if (isBlank(lastSent.get(origin))) return;
		var next = queued.remove(origin);
		lastSent.put(origin, next);
		if (isNotBlank(next)) push(origin);
	}

	private void push(String origin) {
		var root = configs.root();
		if (!root.getPushOrigins().contains(origin)) return;
		logger.info(" {} Pushing remotes", origin);
		Instant lastModified = null;
		while (true) {
			var maybeRef = refRepository.findAll(RefFilter.builder()
				.origin(origin)
				.query("+plugin/origin/push:!+plugin/error")
				.modifiedAfter(lastModified)
				.build().spec(), PageRequest.of(0, 1, by(Ref_.MODIFIED)));
			if (maybeRef.isEmpty()) break;
			var ref = maybeRef.getContent().getFirst();
			lastModified = ref.getModified();
			replicator.push(ref);
		}
		logger.info("{} Finished pushing remotes.", origin);
		taskScheduler.schedule(() -> checkIfQueued(origin), Instant.now().plusMillis(props.getPushCooldownSec() * 1000L));
	}

}
