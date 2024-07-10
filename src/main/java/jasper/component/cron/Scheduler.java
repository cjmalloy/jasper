package jasper.component.cron;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.ConfigCache;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.proj.HasTags;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasTags.hasMatchingTag;
import static jasper.plugin.Cron.getCron;
import static org.springframework.data.domain.Sort.by;

@Component
public class Scheduler {
	private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);

	@Qualifier("cronScheduler")
	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ConfigCache configs;

	@Autowired
	Tagger tagger;

	Map<String, ScheduledFuture<?>> tasks = new HashMap<>();

	Map<String, CronRunner> tags = new HashMap<>();

	/**
	 * Register a runner for a tag.
	 */
	public void addCronTag(String plugin, CronRunner r) {
		tags.put(plugin, r);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		taskScheduler.schedule(this::reload, Instant.now().plusMillis(1000L));
	}

	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		schedule(message.getPayload());
	}

	private void schedule(HasTags ref) {
		var root = configs.root();
		if (root.getCronOrigins() == null) return;
		var key = getKey(ref);
		var existing = tasks.get(key);
		if (existing != null) {
			existing.cancel(false);
			tasks.remove(key);
		}
		if (ref.getTags() == null) return;
		if (hasMatchingTag(ref, "+plugin/error")) return;
		if (!hasMatchingTag(ref, "+plugin/cron")) return;
		var origin = ref.getOrigin();
		var url = ref.getUrl();
		if (!root.getCronOrigins().contains(origin(origin))) return;
		var config = getCron(refRepository.findOneByUrlAndOrigin(url, origin).orElse(null));
		if (config == null || config.getInterval() == null) return;
		if (config.getInterval().toMinutes() < 1) {
			tagger.attachError(url, origin, "Cron Error: Interval too small", config.getInterval().toString());
		} else {
			tasks.put(key, taskScheduler.scheduleWithFixedDelay(
				() -> runSchedule(url, origin),
				Instant.now().plus(config.getInterval()),
				config.getInterval()));
		}
	}

	private void reload() {
		tasks.forEach((key, c) -> c.cancel(false));
		tasks.clear();
		for (String origin : configs.root().getAsyncOrigins()) {
			Instant lastModified = null;
			while (true) {
				var maybeRef = refRepository.findAll(RefFilter.builder()
					.origin(origin)
					.query("+plugin/cron:!+plugin/error")
					.modifiedAfter(lastModified != null ? lastModified : Instant.now().minus(1, ChronoUnit.DAYS))
					.build().spec(), PageRequest.of(0, 1, by(Ref_.MODIFIED)));
				if (maybeRef.isEmpty()) return;
				var ref = maybeRef.getContent().getFirst();
				lastModified = ref.getModified();
				schedule(ref);
			}
		}
	}

	private String getKey(HasTags ref) {
		return ref.getOrigin() + ":" + ref.getUrl();
	}

	private void runSchedule(String url, String origin) {
		var ref = refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
		if (ref == null) {
			var key = origin + ":" + url;
			var existing = tasks.get(key);
			if (existing != null) {
				existing.cancel(false);
				tasks.remove(key);
			}
			return;
		}
		tags.forEach((k, v) -> {
			if (!hasMatchingTag(ref, k)) return;
			logger.debug("{} Cron Tag: {} {}", origin, k, url);
			try {
				v.run(ref);
			} catch (Exception e) {
				logger.error("{} Error in async tag {} ", origin, k, e);
			}
		});
	}

	public interface CronRunner {
		void run(Ref ref) throws Exception;
	}

}
