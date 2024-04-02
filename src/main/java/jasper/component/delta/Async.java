package jasper.component.delta;

import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.service.TaggingService;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static jasper.domain.proj.HasOrigin.origin;
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
	TaggingService taggingService;

	@Autowired
	ConfigCache configs;

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
		var root = configs.root();
		if (root.getAsyncOrigins() == null) return;
		var ud = message.getPayload();
		if (ud.getTags() == null) return;
		if (!root.getAsyncOrigins().contains(origin(ud.getOrigin()))) return;
		tags.forEach((k, v) -> {
			if (!ud.getTags().contains(k)) return;
			if (v instanceof AsyncRunner r) {
				taggingService.create(ud.getUrl(), ud.getOrigin(), r.signature());
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
		responses.forEach((k, v) -> {
			if (!ud.getTags().contains(k)) return;
			if (v instanceof AsyncRunner r) {
				var ref = refRepository.findOneByUrlAndOrigin(ud.getUrl(), ud.getOrigin())
					.orElse(null);
				if (ref != null && ref.hasPluginResponse(r.signature())) return;
			}
			logger.debug("Async Response Tag ({}): {} {}", k, ud.getUrl(), ud.getOrigin());
			try {
				v.run(fetch(ud));
			} catch (NotFoundException e) {
				logger.debug("Plugin not installed {} ", e.getMessage());
			} catch (Exception e) {
				logger.error("Error in async tag response {} ", k, e);
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
			var ref = maybeRef.getContent().get(0);
			lastModified.put(origin, ref.getModified());
			tags.forEach((k, v) -> {
				if (!ref.getTags().contains(k)) return;
				if (v instanceof AsyncRunner r) {
					taggingService.create(ref.getUrl(), ref.getOrigin(), r.signature());
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
	}

	public interface AsyncWatcher {
		void run(Ref ref) throws Exception;
	}

	public interface AsyncRunner extends AsyncWatcher {
		String signature();
	}
}
