package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.domain.proj.Tag;
import jasper.errors.AlreadyExistsException;
import jasper.errors.InvalidPluginException;
import jasper.errors.ModifiedException;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jasper.domain.Ref.from;
import static jasper.domain.proj.Tag.capturesDownwards;
import static jasper.domain.proj.Tag.urlForTag;
import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class Tagger {
	private static final Logger logger = LoggerFactory.getLogger(Tagger.class);
	static final int INIT_PLUGIN_RETRIES = 5;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref internalTag(String url, String origin, String ...tags) {
		return tag(true, true, url, origin, tags);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref tag(String url, String origin, String ...tags) {
		return tag(true, false, url, origin, tags);
	}

	public Ref tag(boolean retry, boolean internal, String url, String origin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (configs.getRemote(origin) != null) return maybeRef.orElse(null);
		if (maybeRef.isEmpty()) {
			var ref = from(url, origin, tags);
			if (internal) ref.addTag("internal");
			try {
				ingest.create(origin, ref);
			} catch (AlreadyExistsException e) {
				return tag(retry, internal, url, origin, tags);
			}
			return ref;
		} else {
			var ref = maybeRef.get();
			if (ref.hasTag(tags)) return ref;
			ref.addTags(asList(tags));
			try {
				ingest.update(origin, ref);
			} catch (InvalidPluginException e) {
				ingest.silent(origin, ref);
			} catch (ModifiedException e) {
				// TODO: infinite retrys?
				if (retry) return tag(true, internal, url, origin, tags);
				return null;
			}
			return ref;
		}
	}

	public Ref remove(String url, String origin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (configs.getRemote(origin) != null) return maybeRef.orElse(null);
		if (maybeRef.isEmpty()) return null;
		var ref = maybeRef.get();
		if (!ref.hasTag(tags)) return ref;
		ref.removeTags(asList(tags));
		try {
			ingest.update(origin, ref);
		} catch (ModifiedException e) {
			// TODO: infinite retrys?
			return remove(url, origin, tags);
		}
		return ref;
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref plugin(String url, String origin, String tag, Object plugin, String ...tags) {
		if (configs.getRemote(origin) != null) return silentPlugin(url, "", origin, tag, plugin, tags);
		return plugin(true, url, origin, null, tag, plugin, tags);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref newPlugin(String url, String title, String origin, String tag, Object plugin, String ...tags) {
		if (configs.getRemote(origin) != null) return silentPlugin(url, title, origin, tag, plugin, tags);
		return plugin(true, url, origin, title, tag, plugin, tags);
	}

	/**
	 * For monkey patching replicated origins.
	 */
	Ref silentPlugin(String url, String title, String origin, String tag, Object plugin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) {
			var ref = from(url, origin, tags).setPlugin(tag, plugin);
			ref.setTitle(title);
			ref.addTag("internal");
			var cursor = refRepository.getCursor(origin);
			if (cursor == null) {
				logger.warn("Silent plugin can't be first!");
				ref.setModified(now().minusMillis(1));
			} else {
				ref.setModified(cursor.minusMillis(1));
			}
			ingest.silent(origin, ref);
			return ref;
		} else {
			var ref = maybeRef.get();
			// TODO: check if plugin already matches exactly and skip
			ref.setPlugin(tag, plugin);
			ref.addTags(asList(tags));
			ingest.silent(origin, ref);
			return ref;
		}
	}

	/**
	 * Set a plugin only if the Ref does not have it yet, and add the source if missing.
	 * Concurrent callers never overwrite each other, so all of them get the Ref with the plugin that won.
	 */
	Ref initPlugin(String source, String url, String origin, String tag, Object plugin, String ...tags) {
		for (var attempt = 0; attempt < INIT_PLUGIN_RETRIES; attempt++) {
			var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
			if (configs.getRemote(origin) != null) return maybeRef.orElse(null);
			if (maybeRef.isEmpty()) {
				var ref = from(url, origin, tags).setPlugin(tag, plugin).addSource(source);
				ref.addTag("internal");
				try {
					ingest.create(origin, ref);
				} catch (AlreadyExistsException e) {
					continue;
				}
				return ref;
			}
			var ref = maybeRef.get();
			var hasSource = isBlank(source) || ref.getSources() != null && ref.getSources().contains(source);
			if (ref.hasPlugin(tag) && hasSource) return ref;
			if (!ref.hasPlugin(tag)) {
				ref.setPlugin(tag, plugin);
				ref.addTags(asList(tags));
			}
			if (!hasSource) ref.addSource(source);
			try {
				ingest.update(origin, ref);
			} catch (ModifiedException e) {
				continue;
			}
			return ref;
		}
		logger.warn("{} Gave up initializing {} on {} after {} conflicts", origin, tag, url, INIT_PLUGIN_RETRIES);
		return refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
	}

	Ref plugin(boolean retry, String url, String origin, String title, String tag, Object plugin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (configs.getRemote(origin) != null) return maybeRef.orElse(null);
		if (maybeRef.isEmpty()) {
			var ref = from(url, origin, tags).setPlugin(tag, plugin);
			ref.setTitle(title);
			ref.addTag("internal");
			try {
				ingest.create(origin, ref);
			} catch (AlreadyExistsException e) {
				return plugin(retry, url, origin, title, tag, plugin, tags);
			}
			return ref;
		} else {
			var ref = maybeRef.get();
			// TODO: check if plugin already matches exactly and skip
			ref.setPlugin(tag, plugin);
			ref.addTags(asList(tags));
			try {
				ingest.update(origin, ref);
			} catch (ModifiedException e) {
				// TODO: infinite retrys?
				if (retry) return plugin(retry, url, origin, title, tag, plugin, tags);
				return null;
			}
			return ref;
		}
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String url, String origin, String msg) {
		attachLogs(origin, tag(url, origin), "", msg);
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String url, String origin, String title, String logs) {
		attachLogs(origin, tag(url, origin), title, logs);
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String origin, Ref parent, String msg) {
		attachLogs(origin, parent, "", msg);
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String origin, Ref parent, String title, String logs) {
		var remote = configs.getRemote(origin);
		if (remote != null) origin = remote.getOrigin();
		var ref = new Ref();
		ref.setOrigin(origin);
		ref.setUrl("error:" + UUID.randomUUID());
		ref.setSources(List.of(parent.getUrl()));
		ref.setTitle(title);
		ref.setComment(logs);
		var tags = new ArrayList<>(List.of("internal", "+plugin/log"));
		if (parent.hasTag("public")) tags.add("public");
		if (origin.equals(parent.getOrigin()) && parent.getTags() != null) {
			tags.addAll(parent.getTags().stream().filter(t -> capturesDownwards("_user", t)).map(Tag::publicTag).toList());
		}
		ref.setTags(tags);
		ingest.create(origin, ref);
	}

	/**
	 * If the Ref is tagged +plugin/debug, log the caller stack trace and
	 * reply to the Ref with a +plugin/log so the user can see it.
	 */
	public void debug(Ref ref, String msg) {
		if (ref == null || !ref.hasTag("+plugin/debug")) return;
		var logs = msg + " " + ref.getUrl() + "\n\n" +
			"tags: `" + ref.getTags() + "`\n\n" +
			"plugins: `" + ref.getPlugins() + "`\n\n" +
			"```\n" + getStackTrace(new Throwable("+plugin/debug stack trace")) + "```";
		logger.debug("{} +plugin/debug {}", ref.getOrigin(), logs);
		try {
			attachLogs(ref.getOrigin(), ref, "+plugin/debug " + msg, logs);
		} catch (Exception e) {
			logger.warn("{} +plugin/debug Could not attach logs to {}", ref.getOrigin(), ref.getUrl(), e);
		}
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String url, String origin, String msg) {
		attachError(origin, tag(url, origin, "+plugin/error"), "", msg);
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String url, String origin, String title, String logs) {
		attachError(origin, tag(url, origin, "+plugin/error"), title, logs);
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String origin, Ref parent, String msg) {
		attachError(origin, parent, "", msg);
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String origin, Ref parent, String title, String logs) {
		var remote = configs.getRemote(origin);
		attachLogs(origin, parent, title, logs);
		if (remote == null && !parent.hasTag("+plugin/error")) {
			tag(false, true, parent.getUrl(), parent.getOrigin(), "+plugin/error");
		}
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref getResponseRef(String user, String origin, String url) {
		var userUrl = urlForTag(url, user);
		return refRepository.findOneByUrlAndOrigin(userUrl, origin).map(ref -> {
			if (isNotBlank(url) && (ref.getSources() == null || !ref.getSources().contains(url))) {
				ref.setSources(new ArrayList<>(List.of(url)));
			}
			if (ref.getTags() == null || ref.hasTag("plugin/deleted")) {
				ref.setTags(new ArrayList<>(List.of("internal", user)));
			}
			return ref;
		})
		.orElseGet(() -> {
			var ref = new Ref();
			ref.setUrl(userUrl);
			ref.setOrigin(origin);
			if (isNotBlank(url)) ref.setSources(new ArrayList<>(List.of(url)));
			ref.setTags(new ArrayList<>(List.of("internal", user)));
			ingest.create(origin, ref);
			return ref;
		});
	}

	@Async
	@Timed(value = "jasper.tagger", histogram = true)
	public void removeAllResponses(String url, String origin, String tag) {
		var remote = configs.getRemote(origin);
		if (remote != null) origin = remote.getOrigin();
		for (var res : refRepository.findAllResponsesWithTag(url, origin, tag)) {
			internalTag(res, origin, "-" + tag);
		}
	}
}
