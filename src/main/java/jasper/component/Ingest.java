package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

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
	EntityManager em;

	@Autowired
	Validate validate;

	@Autowired
	Meta meta;

	@Autowired
	Rng rng;

	@Autowired
	Messages messages;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.ref", histogram = true)
	public void ingest(Ref ref, boolean force) {
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		ref.addHierarchicalTags();
		ref.setCreated(Instant.now());
		ref.setModified(Instant.now());
		validate.ref(ref, force);
		rng.update(ref, null);
		meta.update(ref, null, null);
		ref.setCreated(Instant.now());
		ensureUniqueModified(ref, true);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void update(Ref ref, boolean force) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		ref.addHierarchicalTags();
		ref.setModified(Instant.now());
		validate.ref(ref, force);
		rng.update(ref, maybeExisting.get());
		meta.update(ref, maybeExisting.get(), null);
		ensureUniqueModified(ref, false);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void push(Ref ref, List<String> metadataPlugins) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		ref.addHierarchicalTags();
		ref.setModified(Instant.now());
		validate.ref(ref, true);
		rng.update(ref, maybeExisting.orElse(null));
		meta.update(ref, maybeExisting.orElse(null), metadataPlugins);
		refRepository.save(ref);
		messages.updateRef(ref);
	}

	void ensureUniqueModified(Ref ref, boolean create) {
		var count = 0;
		while (true) {
			try {
				count++;
				ref.setModified(Instant.now(ensureUniqueModifiedClock));
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					if (create) {
						em.persist(ref);
						em.flush();
					} else {
						refRepository.saveAndFlush(ref);
					}
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("ref_pkey".equals(c.getConstraintName())) throw new AlreadyExistsException();
					if ("ref_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
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
