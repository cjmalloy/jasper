package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.component.Remotes;
import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class Push {
	private static final Logger logger = LoggerFactory.getLogger(Push.class);

	@Autowired
	Props props;

	@Autowired
	Remotes remotes;

	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	ConfigCache configs;

	private Map<String, String> lastSent = new ConcurrentHashMap<>();
	private Map<String, String> queued = new ConcurrentHashMap<>();

	@ServiceActivator(inputChannel = "cursorRxChannel")
	public void handleCursorUpdate(Message<String> message) {
		var root = configs.root();
		var origin = (String) message.getHeaders().get("origin");
		if (!root.getPushOrigins().contains(origin)) return;
		var cursor = message.getPayload();
		if (cursor.equals(lastSent.putIfAbsent(origin, cursor))) {
			push(origin);
		} else {
			queued.put(origin, cursor);
		}
	}

	private void clear(String origin) {
		if (isBlank(lastSent.get(origin))) return;
		var next = queued.remove(origin);
		lastSent.put(origin, next);
		if (isNotBlank(next)) push(origin);
	}

	private void push(String origin) {
		var root = configs.root();
		logger.info("Pushing {} {} remotes", root.getScrapeBatchSize(), formatOrigin(origin));
		for (var i = 0; i < root.getPushBatchSize(); i++) {
			if (!remotes.push(origin)) {
				logger.info("All {} remotes up to date.", formatOrigin(origin));
				return;
			}
		}
		logger.info("Finished pushing {} remotes.", formatOrigin(origin));
		taskScheduler.schedule(() -> clear(origin), Instant.now().plusMillis(props.getPushCooldownSec() * 1000L));
	}

}
