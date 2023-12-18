package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Template;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.repository.TemplateRepository;
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
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

@Component
public class IngestTemplate {
	private static final Logger logger = LoggerFactory.getLogger(IngestTemplate.class);

	@Autowired
	Props props;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	EntityManager em;

	@Autowired
	PlatformTransactionManager transactionManager;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.template", histogram = true)
	public void create(Template template) {
		ensureCreateUniqueModified(template);
	}

	@Timed(value = "jasper.template", histogram = true)
	public void update(Template template) {
		ensureUpdateUniqueModified(template);
	}

	@Timed(value = "jasper.template", histogram = true)
	public void push(Template template) {
		try {
			templateRepository.save(template);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	void ensureCreateUniqueModified(Template template) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					template.setModified(Instant.now(ensureUniqueModifiedClock));
					em.persist(template);
					em.flush();
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("template_pkey".equals(c.getConstraintName())) throw new AlreadyExistsException();
					if ("template_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

	void ensureUpdateUniqueModified(Template template) {
		var cursor = template.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					template.setModified(Instant.now(ensureUniqueModifiedClock));
					var updated = templateRepository.optimisticUpdate(
						cursor,
						template.getTag(),
						template.getOrigin(),
						template.getName(),
						template.getConfig(),
						template.getSchema(),
						template.getDefaults(),
						template.getModified());
					if (updated == 0) {
						throw new ModifiedException("Template");
					}
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("template_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

}
