package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static jasper.domain.Ref.from;
import static java.util.Arrays.asList;

@Service
public class Tagger {
	private static final Logger logger = LoggerFactory.getLogger(Tagger.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref tag(String url, String origin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) {
			var ref = from(url, origin, tags);
			ingest.create(ref, false);
			return ref;
		} else {
			var ref = maybeRef.get();
			ref.addTags(asList(tags));
			ingest.update(ref, false);
			return ref;
		}
	}

	@Timed(value = "jasper.tagger", histogram = true)
	public Ref plugin(String url, String origin, String tag, Object plugin, String ...tags) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) {
			var ref = from(url, origin, tags).setPlugin(tag, plugin);
			ingest.create(ref, false);
			return ref;
		} else {
			var ref = maybeRef.get();
			ref.setPlugin(tag, plugin);
			ref.addTags(asList(tags));
			ingest.update(ref, false);
			return ref;
		}
	}
}
