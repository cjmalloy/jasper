package jasper.component.cron;

import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.component.Tagger;
import jasper.component.channel.Watch;
import jasper.domain.Ref;
import jasper.domain.proj.HasTags;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static jasper.domain.proj.HasTags.hasMatchingTag;
import static jasper.plugin.Cron.getCron;
import static jasper.repository.spec.QualifiedTag.tagOriginSelector;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Component
public class Scheduler {
	private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);

	@Qualifier("cronScheduler")
	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ConfigCache configs;

	@Autowired
	Tagger tagger;

	@Autowired
	Watch watch;

	Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

	Map<String, CronRunner> tags = new ConcurrentHashMap<>();

	/**
	 * Register a runner for a tag.
	 */
	public void addCronTag(String plugin, CronRunner r) {
		if (configs.root().getCachedScriptSelectors().stream().noneMatch(s -> s.captures(tagOriginSelector(plugin + s.origin)))) return;
		tags.put(plugin, r);
	}

	@PostConstruct
	public void init() {
		var root = configs.root();
		if (isEmpty(root.getCachedScriptSelectors())) return;
		for (var s : root.getCachedScriptSelectors()) {
			if (!s.captures(tagOriginSelector("+plugin/cron" + s.origin))) continue;
			watch.addWatch(s.origin, "+plugin/cron", this::schedule);
		}
	}

	private void schedule(HasTags ref) {
		var key = getKey(ref);
		var existing = tasks.get(key);
		if (existing != null) {
			existing.cancel(false);
			tasks.remove(key);
		}
		if (!hasMatchingTag(ref, "+plugin/cron")) {
			if (existing != null) logger.info("{} Unscheduled {}: {}", ref.getOrigin(), ref.getTitle(), ref.getUrl());
			return;
		}
		if (hasMatchingTag(ref, "+plugin/error")) {
			if (existing != null) logger.info("{} Unscheduled due to error {}: {}", ref.getOrigin(), ref.getTitle(), ref.getUrl());
			return;
		}
		if (!hasScheduler(ref)) return;
		var origin = ref.getOrigin();
		var url = ref.getUrl();
		var config = getCron(refRepository.findOneByUrlAndOrigin(url, origin).orElse(null));
		if (config == null || config.getInterval() == null) return;
		if (config.getInterval().toMinutes() < 1) {
			tagger.attachError(url, origin, "Cron Error: Interval too small " + config.getInterval());
		} else {
			logger.info("{} Scheduled every {} {}: {}", ref.getOrigin(), config.getInterval(), ref.getTitle(), ref.getUrl());
			tasks.put(key, taskScheduler.scheduleWithFixedDelay(
				() -> runSchedule(url, origin),
				Instant.now().plus(config.getInterval()),
				config.getInterval()));
		}
	}

	private boolean hasScheduler(HasTags ref) {
		for (var tag : tags.keySet()) if (hasMatchingTag(ref, tag)) return true;
		return false;
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
				logger.error("{} Error in cron tag {} ", origin, k, e);
				tagger.attachError(url, origin, "Error in cron tag " + k, e.getMessage());
			}
		});
	}

	public interface CronRunner {
		void run(Ref ref) throws Exception;
	}

}
