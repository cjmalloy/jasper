package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class Ingest {
	private static final Logger logger = LoggerFactory.getLogger(Ingest.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Validate validate;

	@Autowired
	Meta meta;

	void backfillMetadata() {
		List<Ref> all = refRepository.findAll();
		for (var ref : all) {
			meta.update(ref, null);
			refRepository.save(ref);
		}
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void ingest(Ref ref, boolean force) {
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		ref.addHierarchicalTags();
		validate.ref(ref, force);
		meta.update(ref, null);
		ref.setCreated(Instant.now());
		ensureUniqueModified(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void update(Ref ref, boolean force) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		ref.addHierarchicalTags();
		validate.ref(ref, force);
		meta.update(ref, maybeExisting.get());
		ensureUniqueModified(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void push(Ref ref) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		ref.addHierarchicalTags();
		validate.ref(ref, true);
		meta.update(ref, maybeExisting.orElse(null));
		refRepository.save(ref);
	}

	private void ensureUniqueModified(Ref ref) {
		var count = 0;
		while (true) {
			try {
				count++;
				ref.setModified(Instant.now());
				refRepository.save(ref);
				break;
			} catch (DataIntegrityViolationException e) {
				if (count > props.getIngestMaxRetry()) {
					throw new DuplicateModifiedDateException();
				}
			}
		}
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void delete(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return;
		meta.update(null, maybeExisting.get());
		refRepository.deleteByUrlAndOrigin(url, origin);
	}

}
