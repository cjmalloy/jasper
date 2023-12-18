package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

@Component
public class Ingest {
	private static final Logger logger = LoggerFactory.getLogger(Ingest.class);


	private static final TemporalUnit CURSOR_PRECISION = ChronoUnit.MICROS;

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
	PlatformTransactionManager transactionManager;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.ref", histogram = true)
	public void ingest(Ref ref, boolean force) {
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		ref.addHierarchicalTags();
		ref.setCreated(Instant.now());
		validate.ref(ref, force);
		rng.update(ref, null);
		meta.update(ref, null, null);
		ensureCreateUniqueModified(ref);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void update(Ref ref, boolean force) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		ref.addHierarchicalTags();
		validate.ref(ref, force);
		rng.update(ref, maybeExisting.get());
		meta.update(ref, maybeExisting.get(), null);
		ensureUpdateUniqueModified(ref);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void push(Ref ref, List<String> metadataPlugins) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		ref.addHierarchicalTags();
		validate.ref(ref, true);
		rng.update(ref, maybeExisting.orElse(null));
		meta.update(ref, maybeExisting.orElse(null), metadataPlugins);
		refRepository.save(ref);
		messages.updateRef(ref);
	}

	void ensureCreateUniqueModified(Ref ref) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					ref.setModified(Instant.now(ensureUniqueModifiedClock).truncatedTo(CURSOR_PRECISION));
					em.persist(ref);
					em.flush();
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

	void ensureUpdateUniqueModified(Ref ref) {
		var cursor = ref.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					var existing = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin())
						.orElseThrow(() -> new NotFoundException("Ref " + ref.getOrigin() + " " + ref.getUrl()));
					if (!cursorEquals(cursor, existing.getModified())) {
						throw new ModifiedException("Ref");
					}
					ref.setModified(Instant.now(ensureUniqueModifiedClock).truncatedTo(CURSOR_PRECISION));
					refRepository.saveAndFlush(ref);
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("ref_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

	private boolean cursorEquals(Instant a, Instant b) {
		if (a.getEpochSecond() != b.getEpochSecond()) return false;
		return Math.abs(a.getNano() - b.getNano()) < CURSOR_PRECISION.getDuration().getNano();
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void delete(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return;
		meta.update(null, maybeExisting.get(), null);
		refRepository.deleteByUrlAndOrigin(url, origin);
	}

}
