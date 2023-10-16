package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class Rng {
	private static final Logger logger = LoggerFactory.getLogger(Rng.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Timed(value = "jasper.rng.update", histogram = true)
	public void update(Ref ref, Ref existing) {
		if (existing == null) return;
		if (ref.getTags() == null || !ref.getTags().contains("plugin/rng")) return;
		var remotes = refRepository.findAll(RefFilter.builder()
			.url(ref.getUrl())
			.obsolete(true)
			.modifiedAfter(existing.getModified())
			.build().spec(), PageRequest.of(0, 1, Sort.by(Sort.Order.desc(Ref_.MODIFIED))));
		if (remotes.isEmpty()) return;
		var newest = remotes.getContent().get(0);
		if (newest.getOrigin().equals(ref.getOrigin())) return;
		ref.removeTags(List.of("+plugin/rng"));
		ref.addTag("+plugin/rng/" + UUID.randomUUID().toString().toLowerCase().replaceAll("\\W", ""));
	}
}
