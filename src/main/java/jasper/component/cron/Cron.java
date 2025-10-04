package jasper.component.cron;

import io.github.resilience4j.bulkhead.Bulkhead;
import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.component.ScriptExecutorFactory;
import jasper.component.Tagger;
import jasper.component.channel.Watch;
import jasper.domain.Ref;
import jasper.domain.proj.HasTags;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static jasper.domain.proj.HasTags.hasMatchingTag;
import static jasper.plugin.Cron.getCron;
import static jasper.util.Logging.getMessage;
import static java.util.concurrent.CompletableFuture.runAsync;

@Component
public class Cron {
	private static final Logger logger = LoggerFactory.getLogger(Cron.class);

	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	ScriptExecutorFactory scriptExecutorFactory;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ConfigCache configs;

	@Autowired
	Tagger tagger;

	@Autowired
	Watch watch;

	@Autowired
	Bulkhead scriptBulkhead;

	Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
	Map<String, CompletableFuture<?>> refs = new ConcurrentHashMap<>();

	Map<String, CronRunner> tags = new ConcurrentHashMap<>();

	/**
	 * Register a runner for a tag.
	 */
	public void addCronTag(String plugin, CronRunner r) {
		if (!configs.root().script(plugin)) return;
		tags.put(plugin, r);
	}

	@PostConstruct
	public void init() {
		for (var origin : configs.root().scriptOrigins("+plugin/cron")) {
			watch.addWatch(origin, "+plugin/cron", this::schedule);
		}
		for (var origin : configs.root().scriptOrigins("+plugin/user/run")) {
			watch.addWatch(origin, "+plugin/user/run", this::run);
		}
	}

	private void schedule(HasTags ref) {
		var key = getKey(ref);
		var existing = tasks.get(key);
		var cancelled = false;
		if (existing != null && !existing.isDone()) {
			existing.cancel(true);
			tasks.remove(key);
			cancelled = true;
		}
		if (!hasMatchingTag(ref, "+plugin/cron")) {
			if (cancelled) logger.info("{} Unscheduled {}: {}", ref.getOrigin(), ref.getTitle(), ref.getUrl());
			return;
		}
		if (hasMatchingTag(ref, "+plugin/error")) {
			if (cancelled) logger.info("{} Unscheduled due to error {}: {}", ref.getOrigin(), ref.getTitle(), ref.getUrl());
			return;
		}
		if (!hasScheduler(ref)) return;
		var origin = ref.getOrigin();
		if (!configs.root().script("+plugin/cron", origin)) return;
		var url = ref.getUrl();
		var config = getCron(refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(url, origin).orElse(null));
		if (config == null || config.getInterval() == null) return;
		if (config.getInterval().toMinutes() < 1) {
			tagger.attachError(url, origin, "Cron Error: Interval too small " + config.getInterval());
		} else {
			tasks.compute(key, (k, e) -> {
				if (e != null && !e.isDone()) return e;
				if (existing == null) logger.info("{} Scheduled every {} {}: {}", ref.getOrigin(), config.getInterval(), ref.getTitle(), ref.getUrl());
				return taskScheduler.scheduleWithFixedDelay(() -> runSchedule(url, origin),
					Instant.now().plus(config.getInterval()),
					config.getInterval());
			});
		}
	}

	private void run(HasTags target) {
		var origin = target.getOrigin();
		var url = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(target.getUrl(), origin)
			.map(Ref::getSources)
			.map(List::getFirst)
			.orElse(null);
		if (url == null) {
			logger.error("{} Error in run tag: No source", origin);
			tagger.remove(target.getUrl(), origin, "+plugin/user/run");
			return;
		}
		var ref = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(url, origin).orElse(null);
		try {
			if (!configs.root().script("+plugin/user/run", origin)) throw new RuntimeException();
			if (ref == null) {
				logger.warn("{} Can't find Ref (Cannot run on remote origin): {}", origin, url);
				throw new RuntimeException();
			}
			if (hasMatchingTag(ref, "+plugin/error")) {
				logger.info("{} Cancelled running due to error {}: {}", origin, ref.getTitle(), url);
				throw new RuntimeException();
			}
			if (!hasMatchingTag(target, "+plugin/user/run")) {
				// Was cancelled
				throw new RuntimeException();
			}
			var ran = new HashSet<CronRunner>();
			tags.forEach((k, v) -> {
				if (ran.contains(v)) return;
				if (!hasMatchingTag(ref, k)) return;
				if (!configs.root().script(k, origin)) return;
				refs.compute(getKey(ref), (s, existing) -> {
					if (existing != null && !existing.isDone()) return existing;
					return runAsync(() -> {
						logger.warn("{} Run Tag: {} {}", origin, k, url);
						scriptBulkhead.executeSupplier(() -> {
							try {
								v.run(refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(url, origin).orElseThrow());
								ran.add(v);
								tagger.removeAllResponses(url, origin, "+plugin/user/run");
								return null;
							} catch (Exception e) {
								logger.error("{} Error in run tag {} ", origin, k);
								tagger.attachError(url, origin, "Error in run tag " + k, getMessage(e));
								throw new RuntimeException(e);
							} finally {
								refs.remove(s);
							}
						});
					}, scriptExecutorFactory.get(k, origin)).exceptionally(e -> {
						logger.warn("{} Rate limited {} ", origin, k);
						tagger.attachLogs(url, origin, "Rate Limit Hit " + k);
						return null;
					});
				});
			});
		} catch (Exception e) {
			refs.computeIfPresent(getKey(origin, url), (k, existing) -> {
				if (!existing.isDone()) {
					logger.info("{} Cancelled run {}: {}", origin, ref == null ? "" : ref.getTitle(), url);
					existing.cancel(true);
				}
				return null;
			});
			tagger.removeAllResponses(url, origin, "+plugin/user/run");
		}
	}

	private boolean hasScheduler(HasTags ref) {
		for (var tag : tags.keySet()) if (hasMatchingTag(ref, tag)) return true;
		return false;
	}

	private String getKey(HasTags ref) {
		return ref.getOrigin() + ":" + ref.getUrl();
	}


	private String getKey(String origin, String url) {
		return origin + ":" + url;
	}

	private void runSchedule(String url, String origin) {
		var ref = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(url, origin).orElse(null);
		if (ref == null) {
			var key = origin + ":" + url;
			var existing = tasks.get(key);
			if (existing != null && !existing.isDone()) {
				existing.cancel(false);
				tasks.remove(key);
			}
			return;
		}
		if (ref.hasPluginResponse("+plugin/user/run")) {
			// Skip scheduled run since we are running manually
			return;
		}
		var ran = new HashSet<CronRunner>();
		tags.forEach((k, v) -> {
			if (ran.contains(v)) return;
			if (!hasMatchingTag(ref, k)) return;
			if (!configs.root().script(k, origin)) return;
			logger.debug("{} Cron Tag: {} {}", origin, k, url);
			refs.compute(getKey(ref), (s, existing) -> {
				if (existing != null && !existing.isDone()) return existing;
				return runAsync(() -> {
					scriptBulkhead.executeSupplier(() -> {
						try {
							v.run(ref);
							ran.add(v);
						} catch (Exception e) {
							logger.error("{} Error in cron tag {} ", origin, k);
							tagger.attachError(url, origin, "Error in cron tag " + k, getMessage(e));
						}
						return null;
					});
				}, scriptExecutorFactory.get(k, origin)).exceptionally(e -> {
					logger.warn("{} Rate limited {} ", origin, k);
					tagger.attachLogs(url, origin, "Rate Limit Hit " + k);
					return null;
				});
			});
		});
	}

	public interface CronRunner {
		void run(Ref ref) throws Exception;
	}

}
