package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
public class Meta {
	private static final Logger logger = LoggerFactory.getLogger(Meta.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Async
	@Transactional
	@Timed(value = "jasper.meta.update", histogram = true)
	public void update(Ref ref, Ref existing) {
		update(ref, existing, ref == null ? existing.getOrigin() : ref.getOrigin());
	}

	@Transactional
	@Timed(value = "jasper.meta.update", histogram = true)
	public void update(Ref ref, Ref existing, String validationOrigin) {
		if (ref != null) {
			// Creating or updating (not deleting)
			refRepository.updateMetadataByUrlAndOrigin(ref.getUrl(), ref.getOrigin(), validationOrigin);
		}
		if (ref != null && ref.getSources() != null) {
			// Added sources
			for (var source : ref.getSources()) {
				if (existing != null && existing.getSources() != null && existing.getSources().contains(source)) continue;
				refRepository.updateMetadataByUrl(source, validationOrigin);
			}
		}
		if (existing != null && existing.getSources() != null) {
			// Updating or deleting (not creating)
			for (var source : existing.getSources()) {
				// Removed sources
				if (ref != null && ref.getSources() != null && ref.getSources().contains(source)) continue;
				refRepository.updateMetadataByUrl(source, validationOrigin);
			}
		}
	}
}
