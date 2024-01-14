package jasper.component.scheduler;

import jasper.component.Ingest;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.domain.Sort.by;

/**
 * An async service runs by querying a tag, and is marked as completed with the protected version of
 * the same tag. If the tag is also a seal it will be removed on edit.
 */
@Component
public class Async {
	private static final Logger logger = LoggerFactory.getLogger(Async.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	Map<String, Boolean> changes = new HashMap<>();
	Map<String, Instant> lastModified = new HashMap<>();

	Map<String, AsyncWatcher> tags = new HashMap<>();
	Map<String, AsyncWatcher> responses = new HashMap<>();


	/**
	 * Register a runner for a tag.
	 */
	public void addAsyncTag(String plugin, AsyncWatcher r) {
		tags.put(plugin, r);
	}

	/**
	 * Register a runner for a tag response.
	 */
	public void addAsyncResponse(String plugin, AsyncWatcher r) {
		responses.put(plugin, r);
	}

	/**
	 * The tracking query the plugin tag but not the protected version.
	 */
	String trackingQuery() {
		var plugins = new ArrayList<>(Arrays.asList(tags.keySet().stream().map(p -> p + ":!+" + p).toArray(String[]::new)));
		plugins.addAll(responses.keySet());
		return String.join("|", plugins);
	}

	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		var ref = message.getPayload();
		if (ref.getTags() == null) return;
		if (changes.get(ref.getOrigin()) != null) return;
		if (!Arrays.asList(props.getAsyncOrigins()).contains(ref.getOrigin())) return;
		for (var tag : ref.getTags()) {
			if (tags.containsKey(tag) || responses.containsKey(tag)) {
				changes.put(ref.getOrigin(), true);
				return;
			}
		}
	}

	@Scheduled(
		fixedRateString = "${jasper.async-interval-sec}",
		initialDelayString = "${jasper.async-delay-sec}",
		timeUnit = TimeUnit.SECONDS)
	public void drainAsyncTask() {
		if (changes.isEmpty()) return;
		if (tags.isEmpty() && responses.isEmpty()) return;
		for (var origin : props.getAsyncOrigins()) drain(origin);
	}

	private void drain(String origin) {
		if (changes.get(origin) == null || lastModified.get(origin) == null) return;
		changes.remove(origin);
		for (var i = 0; i < props.getAsyncBatchSize(); i++) {
			var maybeRef = refRepository.findAll(RefFilter.builder()
				.origin(origin)
				.query(trackingQuery())
				.modifiedAfter(lastModified.getOrDefault(origin, Instant.now().minus(1, ChronoUnit.DAYS)))
				.build().spec(), PageRequest.of(0, 1, by(Ref_.MODIFIED)));
			if (maybeRef.isEmpty()) return;
			var ref = maybeRef.getContent().get(0);
			lastModified.put(origin, ref.getModified());
			tags.forEach((k, v) -> {
				if (!ref.getTags().contains(k)) return;
				if (v instanceof AsyncRunner r) {
					ref.getTags().add(r.signature());
					ingest.update(ref, false);
				}
				try {
					v.run(ref);
				} catch (NotFoundException e) {
					logger.debug("Plugin not installed {} ", e.getMessage());
				} catch (Exception e) {
					logger.error("Error in async tag {} ", k, e);
				}
			});
			responses.forEach((k, v) -> {
				if (!ref.getTags().contains(k)) return;
				if (v instanceof AsyncRunner r) {
					if (ref.hasPluginResponse(r.signature())) return;
				}
				try {
					v.run(ref);
				} catch (NotFoundException e) {
					logger.debug("Plugin not installed {} ", e.getMessage());
				} catch (Exception e) {
					logger.error("Error in async tag response {} ", k, e);
				}
			});
		}
		// Did not exhaust Refs
		changes.put(origin, true);
	}

	public interface AsyncWatcher {
		void run(Ref ref) throws Exception;
	}

	public interface AsyncRunner extends AsyncWatcher {
		String signature();
	}
}
