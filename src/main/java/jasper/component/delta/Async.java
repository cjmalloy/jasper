package jasper.component.delta;

import jasper.component.ConfigCache;
import jasper.component.ScriptExecutorFactory;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.proj.HasTags;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.repository.spec.QualifiedTag;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasTags.hasMatchingTag;
import static jasper.domain.proj.HasTags.hasPluginResponse;
import static jasper.util.Logging.getMessage;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.domain.Sort.by;

/**
 * An async service runs on Refs by querying a tag.
 * The Ref is considered completed if either:
 * 1. The tag is removed.
 * 2. A signature tag is added.
 * 3. A signature tag is added via plugin response.
 * If either tag is also a seal it will be removed on edit.
 */
@Component
public class Async {
	private static final Logger logger = LoggerFactory.getLogger(Async.class);

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

	Map<String, CompletableFuture<?>> refs = new ConcurrentHashMap<>();
	Map<String, AsyncRunner> tags = new ConcurrentHashMap<>();

	/**
	 * Register a runner for a tag.
	 */
	public void addAsyncTag(String plugin, AsyncRunner r) {
		if (!configs.root().script(plugin)) return;
		tags.put(plugin, r);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		if (tags.isEmpty()) return;
		taskScheduler.schedule(() -> configs.root().getScriptSelectors()
				.stream()
				.map(QualifiedTag::tagOriginSelector)
				.map(s -> s.origin)
				.forEach(this::backfill),
			Instant.now().plusMillis(1000L));
	}

	/**
	 * The tracking query for uncompleted Refs, or Refs which may be completed
	 * by a response Plugin.
	 */
	String trackingQuery() {
		if (tags.isEmpty()) return null;
		return "!+plugin/error" +
			":(" + String.join("|", tags.keySet()) + ")" +
			":(" + String.join("|", configs.root().getScriptSelectors()) + ")";
	}

	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		var ud = message.getPayload();
		var origin = ud.getOrigin();
		try {
			if (tags.isEmpty()) throw new RuntimeException();
			if (isEmpty(configs.root().getScriptSelectors())) throw new RuntimeException();
			if (ud.getTags() == null) throw new RuntimeException();
			if (hasMatchingTag(ud, "+plugin/error")) throw new RuntimeException();
			tags.forEach((k, v) -> {
				if (!hasMatchingTag(ud, k)) return;
				if (!configs.root().script(k, origin)) return;
				if (isNotBlank(v.signature()) && hasPluginResponse(ud, v.signature())) return;
				logger.debug("{} Async Tag ({}): {} {}", origin, k, ud.getUrl(), origin);
				refs.compute(getKey(ud), (u, existing) -> {
					if (existing != null && !existing.isDone()) {
						logger.debug("{} Async tag trying to run before finishing {} ", origin, k);
						return existing;
					}
					return runAsync(() -> {
						try {
							v.run(fetch(ud));
						} catch (NotFoundException e) {
							logger.debug("{} Plugin not installed {} ", origin, getMessage(e));
						} catch (Exception e) {
							logger.error("{} Error in async tag {} ", origin, k, e);
						}
					}, scriptExecutorFactory.get(k, origin)).exceptionally(e -> {
						logger.warn("{} Rate limited {} ", origin, k);
						tagger.attachError(ud.getUrl(), origin, "Rate Limit Hit " + k);
						return null;
					});
				});
			});
		} catch (Exception e) {
			refs.computeIfPresent(getKey(ud), (k, existing) -> {
				if (existing.isDone()) return null;
				logger.info("{} Cancelled run {}: {}", origin, ud.getTitle(), ud.getUrl());
				existing.cancel(true);
				return null;
			});
		}
	}

	private Ref fetch(RefDto ud) {
		return refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(ud.getUrl(), origin(ud.getOrigin()))
			.orElseThrow(() -> new NotFoundException("Async"));
	}

	private void backfill(String origin) {
		Instant lastModified = null;
		while (true) {
			var maybeRef = refRepository.findAll(RefFilter.builder()
				.origin(origin)
				.query(trackingQuery())
				.modifiedAfter(lastModified != null ? lastModified : Instant.now().minus(1, ChronoUnit.DAYS))
				.build().spec(), PageRequest.of(0, 1, by(Ref_.MODIFIED)));
			if (maybeRef.isEmpty()) return;
			var ref = maybeRef.getContent().getFirst();
			lastModified = ref.getModified();
			tags.forEach((k, v) -> {
				if (!v.backfill()) return;
				if (!configs.root().script(k, origin)) return;
				if (!hasMatchingTag(ref, k)) return;
				// TODO: Only check plugin responses in the same origin
				if (isNotBlank(v.signature()) && ref.hasPluginResponse(v.signature())) return;
				runAsync(() -> {
					try {
						v.run(ref);
					} catch (NotFoundException e) {
						logger.debug("{} Plugin not installed {} ", ref.getOrigin(), getMessage(e));
					} catch (Exception e) {
						logger.error("{} Error in async tag {} ", ref.getOrigin(), k, e);
					}
				}, scriptExecutorFactory.get(k, origin)).exceptionally(e -> {
					logger.warn("{} Rate limited {} ", origin, k);
					tagger.attachError(ref.getUrl(), origin, "Rate Limit Hit " + k);
					return null;
				});
			});
		}
	}

	private String getKey(HasTags ref) {
		return ref.getOrigin() + ":" + ref.getUrl();
	}

	public interface AsyncRunner {
		void run(Ref ref) throws Exception;
		/**
		 * Mark this Ref as completed with this signature on the Ref itself
		 * or as a Plugin response.
		 */
		default String signature() {
			return null;
		}
		/**
		 * Check for uncompleted Refs on server restart.
		 */
		default boolean backfill() {
			return true;
		}
	}
}
