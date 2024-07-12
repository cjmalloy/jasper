package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jasper.domain.Ref.from;
import static jasper.domain.proj.Tag.matchesTag;
import static java.util.Arrays.asList;

@Service
public class Tagger {
	private static final Logger logger = LoggerFactory.getLogger(Tagger.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref internalTag(String url, String origin, String ...tags) {
		return tag(true, url, origin, tags);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref tag(String url, String origin, String ...tags) {
		return tag(false, url, origin, tags);
	}

	public Ref tag(boolean internal, String url, String origin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) {
			var ref = from(url, origin, tags);
			if (internal) ref.addTag("internal");
			ingest.create(ref, false);
			return ref;
		} else {
			var ref = maybeRef.get();
			if (ref.hasTag(tags)) return ref;
			ref.addTags(asList(tags));
			ingest.update(ref, false);
			return ref;
		}
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref plugin(String url, String origin, String tag, Object plugin, String ...tags) {
		return plugin(false, url, origin, tag, plugin, tags);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref internalPlugin(String url, String origin, String tag, Object plugin, String ...tags) {
		return plugin(true, url, origin, tag, plugin, tags);
	}

	private Ref plugin(boolean internal, String url, String origin, String tag, Object plugin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) {
			var ref = from(url, origin, tags).setPlugin(tag, plugin);
			if (internal) ref.addTag("internal");
			ingest.create(ref, false);
			return ref;
		} else {
			var ref = maybeRef.get();
			// TODO: check if plugin already matches exactly and skip
			ref.setPlugin(tag, plugin);
			ref.addTags(asList(tags));
			ingest.update(ref, false);
			return ref;
		}
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String url, String origin, String msg) {
		attachLogs(origin, tag(url, origin, "+plugin/error"), "", msg);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String url, String origin, String title, String logs) {
		attachLogs(origin, tag(url, origin, "+plugin/error"), title, logs);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String origin, Ref parent, String msg) {
		attachLogs(origin, parent, "", msg);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachLogs(String origin, Ref parent, String title, String logs) {
		var ref = new Ref();
		ref.setOrigin(origin);
		ref.setUrl("error:" + UUID.randomUUID());
		ref.setSources(List.of(parent.getUrl()));
		ref.setTitle(title);
		ref.setComment(logs);
		var tags = new ArrayList<>(List.of("internal", "+plugin/log"));
		if (parent.hasTag("public")) tags.add("public");
		tags.addAll(parent.getTags().stream().filter(t -> matchesTag("+user", t) || matchesTag("_user", t)).toList());
		ref.setTags(tags);
		ingest.create(ref, false);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String url, String origin, String msg) {
		attachError(origin, tag(url, origin, "+plugin/error"), "", msg);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String url, String origin, String title, String logs) {
		attachError(origin, tag(url, origin, "+plugin/error"), title, logs);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String origin, Ref parent, String msg) {
		attachError(origin, parent, "", msg);
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public void attachError(String origin, Ref parent, String title, String logs) {
		attachLogs(origin, parent, title, logs);
		if (!parent.hasTag("+plugin/error")) {
			parent.addTag("+plugin/error");
			ingest.update(parent, false);
		}
	}
}
