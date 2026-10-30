package jasper.component.channel;

import jasper.domain.Ref_;
import jasper.domain.proj.HasTags;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasTags.hasMatchingTag;
import static jasper.repository.spec.QualifiedTag.selector;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.domain.Sort.by;

@Component()
public class Watch {
	private static final Logger logger = LoggerFactory.getLogger(Watch.class);

	@Autowired
	RefRepository refRepository;

	Map<String, Map<String, Watcher>> watchers = new ConcurrentHashMap<>();
	Map<String, Map<String, Set<String>>> watching = new ConcurrentHashMap<>();

	private final AtomicBoolean ready = new AtomicBoolean(false);

	/**
	 * Register a watcher to watch all Refs in an origin.
	 * The origin may be a wildcard selector such as "@*" or "@origin.*".
	 */
	public void addWatch(String origin, Watcher w) {
		watchers.computeIfAbsent(origin, o -> new ConcurrentHashMap<>()).put("", w);
	}

	/**
	 * Register a watcher for all tagged Refs in an origin.
	 * The origin may be a wildcard selector such as "@*" or "@origin.*".
	 * If the application has already started, existing Refs are sent to
	 * the new watcher.
	 */
	public void addWatch(String origin, String tag, Watcher w) {
		var existing = watchers.computeIfAbsent(origin, o -> new ConcurrentHashMap<>()).put(tag, w);
		if (existing == null && ready.get()) scan(origin, tag);
	}

	/**
	 * Remove a watcher for tagged Refs in an origin.
	 * Tag removal tracking is kept, since it is keyed by concrete origin
	 * and may still be needed by other watchers covering the same origin.
	 */
	public void removeWatch(String origin, String tag) {
		watchers.computeIfPresent(origin, (o, ws) -> {
			ws.remove(tag);
			return ws.isEmpty() ? null : ws;
		});
	}

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		ready.set(true);
		for (var origin : watchers.keySet()) {
			for (var tag : watchers.get(origin).keySet()) {
				scan(origin, tag);
			}
		}
	}

	private void scan(String origin, String tag) {
		if (isBlank(tag)) return;
		try {
			Instant lastModified = null;
			while (true) {
				var maybeRef = refRepository.findAll(RefFilter.builder()
					.origin(origin)
					.query(tag)
					.modifiedAfter(lastModified)
					.build().spec(), PageRequest.of(0, 1, by(Ref_.MODIFIED)));
				if (maybeRef.isEmpty()) break;
				var ref = maybeRef.getContent().getFirst();
				lastModified = ref.getModified();
				var w = watchers.getOrDefault(origin, Map.of()).get(tag);
				if (w == null) return;
				watching
					.computeIfAbsent(origin(ref.getOrigin()), o -> new ConcurrentHashMap<>())
					.computeIfAbsent(tag, t -> ConcurrentHashMap.newKeySet())
					.add(ref.getUrl());
				try {
					w.notify(ref);
				} catch (Exception e) {
					logger.warn("{} Error starting watcher {}", origin, tag, e);
				}
			}
		} catch (Exception e) {
			logger.error("{} Error serializing watchers {}", origin, tag, e);
		}
	}

	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		var origin = origin(message.getHeaders().get("origin").toString());
		var target = selector(origin);
		var matching = new HashMap<String, Watcher>();
		watchers.forEach((o, ws) -> {
			if (selector(o).captures(target)) ws.forEach(matching::putIfAbsent);
		});
		if (matching.isEmpty()) return;
		var ref = message.getPayload();
		matching.forEach((tag, w) -> {
			var set = watching
				.computeIfAbsent(origin, o -> new ConcurrentHashMap<>())
				.computeIfAbsent(tag, t -> ConcurrentHashMap.newKeySet());
			if (isNotBlank(tag)) {
				if (!set.contains(ref.getUrl())) {
					if (!hasMatchingTag(ref, tag)) return;
					set.add(ref.getUrl());
				}
			}
			try {
				w.notify(ref);
			} finally {
				if (isNotBlank(tag) && !hasMatchingTag(ref, tag)) {
					set.remove(ref.getUrl());
				}
			}
		});
	}

	public interface Watcher {
		void notify(HasTags ref);
	}

}
