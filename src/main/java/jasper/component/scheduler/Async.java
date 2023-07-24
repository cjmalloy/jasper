package jasper.component.scheduler;

import jasper.component.Ingest;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

	Map<String, Instant> lastModified = new HashMap<>();

	Map<String, AsyncRunner> tags = new HashMap<>();
	Map<String, AsyncRunner> responses = new HashMap<>();

	/**
	 * Register a runner for a tag.
	 */
	public void addAsyncTag(String plugin, AsyncRunner r) {
		tags.put(plugin, r);
	}

	/**
	 * Register a runner for a tag response.
	 */
	public void addAsyncResponse(String plugin, AsyncRunner r) {
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

	@Scheduled(
		fixedRateString = "${jasper.async-interval-sec}",
		initialDelayString = "${jasper.async-delay-sec}",
		timeUnit = TimeUnit.SECONDS)
	public void drainAsyncTask() {
		if (tags.isEmpty() && responses.isEmpty()) return;
		for (var origin : props.getScrapeOrigins()) drain(origin);
	}

	private void drain(String origin) {
		while (true) {
			var maybeRef = refRepository.findAll(RefFilter.builder()
				.origin(origin)
				.query(trackingQuery())
				.modifiedAfter(lastModified.getOrDefault(origin, Instant.now().minus(1, ChronoUnit.DAYS)))
				.build().spec(), PageRequest.of(0, 1, Sort.by("modified")));
			if (maybeRef.isEmpty()) return;
			var ref = maybeRef.getContent().get(0);
			lastModified.put(origin, ref.getModified());
			tags.forEach((k, v) -> {
				if (!ref.getTags().contains(k)) return;
				ref.getTags().add(v.signature());
				ingest.update(ref, false);
				try {
					v.run(ref);
				} catch (Exception e) {
					logger.error("Error in async tag {} ", k, e);
				}
			});
			responses.forEach((k, v) -> {
				if (!ref.getTags().contains(k)) return;
				if (ref.hasPluginResponse(v.signature())) return;
				try {
					v.run(ref);
				} catch (Exception e) {
					logger.error("Error in async tag response {} ", k, e);
				}
			});
		}
	}

	public interface AsyncRunner {
		void run(Ref ref) throws Exception;
		String signature();
	}
}
