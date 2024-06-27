package jasper.component.channel.delta;

import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static jasper.domain.proj.HasOrigin.origin;
import static jasper.domain.proj.HasTags.hasMatchingTag;
import static org.apache.commons.lang3.StringUtils.isBlank;
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
	Props props;

	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ConfigCache configs;

	Map<String, AsyncRunner> tags = new HashMap<>();

	@PostConstruct
	public void init() {
		taskScheduler.schedule(() -> {
			for (String origin : configs.root().getAsyncOrigins()) {
				backfill(origin);
			}
		}, Instant.now().plusMillis(1000L));
	}

	/**
	 * Register a runner for a tag.
	 */
	public void addAsyncTag(String plugin, AsyncRunner r) {
		tags.put(plugin, r);
	}

	/**
	 * The tracking query for uncompleted Refs, or Refs which may be completed
	 * by a response Plugin.
	 */
	String trackingQuery() {
		return tags
			.entrySet()
			.stream()
			.map(e -> e.getKey() + (isBlank(e.getValue().signature()) ? "" : ":!" + e.getValue().signature()))
			.collect(Collectors.joining("|"));
	}

	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		var root = configs.root();
		if (root.getAsyncOrigins() == null) return;
		var ud = message.getPayload();
		if (ud.getTags() == null) return;
		if (!root.getAsyncOrigins().contains(origin(ud.getOrigin()))) return;
		tags.forEach((k, v) -> {
			if (hasMatchingTag(ud, v.signature())) return;
			if (!hasMatchingTag(ud, k)) return;
			if (isNotBlank(v.signature())) {
				var ref = refRepository.findOneByUrlAndOrigin(ud.getUrl(), ud.getOrigin())
					.orElse(null);
				// TODO: Only check plugin responses in the same origin
				if (ref != null && ref.hasPluginResponse(v.signature())) return;
			}
			logger.debug("Async Tag ({}): {} {}", k, ud.getUrl(), ud.getOrigin());
			try {
				v.run(fetch(ud));
			} catch (NotFoundException e) {
				logger.debug("Plugin not installed {} ", e.getMessage());
			} catch (Exception e) {
				logger.error("Error in async tag {} ", k, e);
			}
		});
	}

	private Ref fetch(RefDto ud) {
		return this.refRepository.findOneByUrlAndOrigin(ud.getUrl(), origin(ud.getOrigin()))
			.orElseThrow(() -> new NotFoundException("Async"));
	}

	private void backfill(String origin) {
		Map<String, Instant> lastModified = new HashMap<>();
		while (true) {
			var maybeRef = refRepository.findAll(RefFilter.builder()
				.origin(origin)
				.query(trackingQuery())
				.modifiedAfter(lastModified.getOrDefault(origin, Instant.now().minus(1, ChronoUnit.DAYS)))
				.build().spec(), PageRequest.of(0, 1, by(Ref_.MODIFIED)));
			if (maybeRef.isEmpty()) return;
			var ref = maybeRef.getContent().getFirst();
			lastModified.put(origin, ref.getModified());
			tags.forEach((k, v) -> {
				if (!v.backfill()) return;
				if (!hasMatchingTag(ref, k)) return;
				// TODO: Only check plugin responses in the same origin
				if (isNotBlank(v.signature()) && ref.hasPluginResponse(v.signature())) return;
				try {
					v.run(ref);
				} catch (NotFoundException e) {
					logger.debug("Plugin not installed {} ", e.getMessage());
				} catch (Exception e) {
					logger.error("Error in async tag {} ", k, e);
				}
			});
		}
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
