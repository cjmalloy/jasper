package jasper.component.channel;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.component.Replicator;
import jasper.config.Props;
import jasper.domain.proj.HasTags;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.domain.proj.HasOrigin.origin;
import static jasper.plugin.Origin.getOrigin;
import static jasper.plugin.Push.getPush;

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

	@Autowired
	Watch watch;

	private Map<String, Instant> lastSent = new ConcurrentHashMap<>();
	private Map<String, Instant> queued = new ConcurrentHashMap<>();
	private Map<String, Set<Tuple2<String, String>>> pushes = new HashMap<>();

	@PostConstruct
	public void init() {
		for (var origin : configs.root().getPushOrigins()) {
			watch.addWatch(origin, "+plugin/origin/push", this::watch);
		}
	}

	private void watch(HasTags update) {
		var remote = refRepository.findOneByUrlAndOrigin(update.getUrl(), update.getOrigin())
			.orElseThrow();
		var origin = getOrigin(remote);
		var push = getPush(remote);
		var tuple = Tuple.of(remote.getUrl(), remote.getOrigin());
		pushes.values().forEach(set -> set.remove(tuple));
		if (!remote.hasTag("+plugin/error") && push.isPushOnChange()) {
			pushes
				.computeIfAbsent(origin.getLocal(), o -> new HashSet<>())
				.add(tuple);
		}
	}

	@ServiceActivator(inputChannel = "cursorRxChannel")
	public void handleCursorUpdate(Message<Instant> message) {
		var root = configs.root();
		var origin = origin(message.getHeaders().get("origin").toString());
		if (!root.getPushOrigins().contains(origin)) return;
		var cursor = message.getPayload();
		if (cursor.equals(lastSent.computeIfAbsent(origin, o -> cursor))) {
			push(origin);
		} else {
			queued.put(origin, cursor);
		}
	}

	private void push(String origin) {
		logger.info("{} Pushing remotes", origin);
		try {
			if (pushes.containsKey(origin)) {
				var deleted = new HashSet<Tuple2<String, String>>();
				pushes.get(origin).forEach(tuple -> {
					var remote = refRepository.findOneByUrlAndOrigin(tuple._1, tuple._2);
					if (remote.isPresent()) {
						replicator.push(remote.get());
					} else {
						deleted.add(tuple);
					}
				});
				deleted.forEach(remote -> pushes.values().forEach(set -> set.remove(remote)));
			}
		} finally {
			taskScheduler.schedule(() -> checkIfQueued(origin), Instant.now().plusMillis(props.getPushCooldownSec() * 1000L));
		}
	}

	private void checkIfQueued(String origin) {
		if (lastSent.containsKey(origin)) return;
		var next = queued.remove(origin);
		if (next != null) {
			lastSent.put(origin, next);
			push(origin);
		} else {
			lastSent.remove(origin);
		}
	}

}
