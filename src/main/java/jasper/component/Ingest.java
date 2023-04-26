package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;

import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;

@Component
public class Ingest {
	private static final Logger logger = LoggerFactory.getLogger(Ingest.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	Validate validate;

	@Autowired
	Meta meta;

	@Autowired
	Auth auth;

	@Timed(value = "jasper.ref", histogram = true)
	public void ingest(Ref ref, boolean force) {
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		if (!auth.hasRole(MOD)) ref.removeTags(Arrays.asList(props.getModSeals()));
		if (!auth.hasRole(EDITOR)) ref.removeTags(Arrays.asList(props.getEditorSeals()));
		ref.addHierarchicalTags();
		validate.ref(ref, force);
		meta.update(ref, null, null);
		ref.setCreated(Instant.now());
		ensureUniqueModified(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void update(Ref ref, boolean force) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		if (!auth.hasRole(MOD)) ref.removeTags(Arrays.asList(props.getModSeals()));
		if (!auth.hasRole(EDITOR)) ref.removeTags(Arrays.asList(props.getEditorSeals()));
		ref.addHierarchicalTags();
		validate.ref(ref, force);
		meta.update(ref, maybeExisting.get(), null);
		ensureUniqueModified(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void push(Ref ref) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		ref.addHierarchicalTags();
		validate.ref(ref, true);
		meta.update(ref, maybeExisting.orElse(null), null);
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
		meta.update(null, maybeExisting.get(), null);
		refRepository.deleteByUrlAndOrigin(url, origin);
	}

}
