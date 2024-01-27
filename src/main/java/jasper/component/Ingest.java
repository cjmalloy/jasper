package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
	ObjectMapper objectMapper;

	@Autowired
	PlatformTransactionManager transactionManager;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.ref", histogram = true)
	public void create(Ref ref, boolean force) {
		ref.addHierarchicalTags();
		ref.setCreated(Instant.now());
		validate.ref(ref, force);
		rng.update(ref, null);
		meta.ref(ref, null);
		ensureCreateUniqueModified(ref);
		meta.sources(ref, null, null);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void update(Ref ref, boolean force) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		ref.addHierarchicalTags();
		validate.ref(ref, force);
		rng.update(ref, maybeExisting.get());
		meta.ref(ref, null);
		ensureUpdateUniqueModified(ref);
		meta.sources(ref, maybeExisting.get(), null);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void push(Ref ref, List<String> metadataPlugins) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		ref.addHierarchicalTags();
		validate.ref(ref, true);
		rng.update(ref, maybeExisting.orElse(null));
		meta.ref(ref, metadataPlugins);
		try {
			refRepository.save(ref);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
		meta.sources(ref, maybeExisting.orElse(null), metadataPlugins);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void delete(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return;
		refRepository.deleteByUrlAndOrigin(url, origin);
		meta.sources(null, maybeExisting.get(), null);
	}

	void ensureCreateUniqueModified(Ref ref) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					ref.setModified(Instant.now(ensureUniqueModifiedClock));
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
					ref.setModified(Instant.now(ensureUniqueModifiedClock));
					var updated = refRepository.optimisticUpdate(
						cursor,
						ref.getUrl(),
						ref.getOrigin(),
						ref.getTitle(),
						ref.getComment(),
						ref.getTags() == null ? null : objectMapper.convertValue(ref.getTags(), ArrayNode.class),
						ref.getSources() == null ? null : objectMapper.convertValue(ref.getSources(), ArrayNode.class),
						ref.getAlternateUrls() == null ? null : objectMapper.convertValue(ref.getAlternateUrls(), ArrayNode.class),
						ref.getPlugins(),
						ref.getMetadata(),
						ref.getPublished(),
						ref.getModified());
					if (updated == 0) {
						throw new ModifiedException("Ref");
					}
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

}
