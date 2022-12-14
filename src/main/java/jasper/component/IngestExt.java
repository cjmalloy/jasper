package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
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

import static jasper.component.Replicator.deletedTag;
import static jasper.component.Replicator.deletorTag;
import static jasper.component.Replicator.isDeletorTag;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class IngestExt {
	private static final Logger logger = LoggerFactory.getLogger(IngestExt.class);

	@Autowired
	Props props;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	EntityManager em;

	@Autowired
	Validate validate;

	@Autowired
	Messages messages;

	@Autowired
	PlatformTransactionManager transactionManager;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.ext", histogram = true)
	public void create(Ext ext, boolean force) {
		if (isDeletorTag(ext.getTag())) {
			if (extRepository.existsByQualifiedTag(deletedTag(ext.getTag()) + ext.getOrigin())) throw new AlreadyExistsException();
		} else {
			delete(deletorTag(ext.getTag()) + ext.getOrigin());
		}
		validate.ext(ext.getOrigin(), ext, force);
		ensureCreateUniqueModified(ext);
		messages.updateExt(ext);
	}

	@Timed(value = "jasper.ext", histogram = true)
	public void update(Ext ext, boolean force) {
		if (!extRepository.existsByQualifiedTag(ext.getTag() + ext.getOrigin())) throw new NotFoundException("Ext");
		validate.ext(ext.getOrigin(), ext, force);
		ensureUpdateUniqueModified(ext);
		messages.updateExt(ext);
	}

	@Timed(value = "jasper.ext", histogram = true)
	public void push(Ext ext, String templateOrigin, boolean validation) {
		if (validation) validate.ext(ext.getOrigin(), ext, templateOrigin, true);
		try {
			extRepository.save(ext);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
		if (isDeletorTag(ext.getTag())) {
			delete(deletedTag(ext.getTag()) + ext.getOrigin());
		} else {
			delete(deletorTag(ext.getTag()) + ext.getOrigin());
		}
		messages.updateExt(ext);
	}

	@Timed(value = "jasper.ext", histogram = true)
	public void delete(String qualifiedTag) {
		extRepository.deleteByQualifiedTag(qualifiedTag);
	}

	void ensureCreateUniqueModified(Ext ext) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					ext.setModified(Instant.now(ensureUniqueModifiedClock));
					em.persist(ext);
					em.flush();
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (e instanceof ConstraintViolationException c) {
					if ("ext_pkey".equals(c.getConstraintName())) throw new AlreadyExistsException();
					if ("ext_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

	void ensureUpdateUniqueModified(Ext ext) {
		var cursor = ext.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					ext.setModified(Instant.now(ensureUniqueModifiedClock));
					var updated = extRepository.optimisticUpdate(
						cursor,
						ext.getTag(),
						ext.getOrigin(),
						ext.getName(),
						ext.getConfig(),
						ext.getModified());
					if (updated == 0) {
						throw new ModifiedException("Ext");
					}
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("ext_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

}
