package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import jasper.config.Props;
import jasper.domain.Template;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.InvalidPushException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.TemplateRepository;
import jasper.util.Archive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static jasper.component.Replicator.deletedTag;
import static jasper.component.Replicator.deletorTag;
import static jasper.component.Replicator.isDeletorTag;
import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;
import static jasper.util.Archive.isBlank;
import static jasper.util.Archive.nextModified;
import static jasper.util.DbConstraint.isPkViolation;
import static jasper.util.DbConstraint.isUniqueModifiedOriginViolation;

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
	Validate validate;

	@Autowired
	Messages messages;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Value("#{environment.matchesProfiles('archive')}")
	boolean archive;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.template", histogram = true)
	public void create(Template template) {
		// In archive mode the current version is checked when appending
		if (!archive) {
			if (isDeletorTag(template.getTag())) {
				if (templateRepository.existsByQualifiedTag(deletedTag(template.getQualifiedTag()))) throw new AlreadyExistsException();
			} else {
				delete(deletorTag(template.getQualifiedTag()));
			}
		}
		validate.template(template.getOrigin(), template);
		ensureCreateUniqueModified(template, true);
		messages.updateTemplate(template);
	}

	@Timed(value = "jasper.template", histogram = true)
	public void update(Template template) {
		if (archive ? current(template.getQualifiedTag()).isEmpty() : !templateRepository.existsByQualifiedTag(template.getQualifiedTag())) throw new NotFoundException("Template");
		validate.template(template.getOrigin(), template);
		ensureUpdateUniqueModified(template);
		messages.updateTemplate(template);
	}

	@Timed(value = "jasper.template", histogram = true)
	public void push(Template template) {
		validate.template(template.getOrigin(), template);
		try {
			templateRepository.save(template);
		} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
			if (e instanceof EntityExistsException) throw new AlreadyExistsException();
			if (isPkViolation(e, "template")) throw new AlreadyExistsException();
			if (isUniqueModifiedOriginViolation(e, "template")) throw new DuplicateModifiedDateException();
			throw e;
		} catch (TransactionSystemException e) {
			if (e.getCause() instanceof RollbackException r) {
				if (r.getCause() instanceof jakarta.validation.ConstraintViolationException) throw new InvalidPushException();
			}
			throw e;
		}
		// In archive mode pushes never remove rows
		if (!archive && isDeletorTag(template.getTag())) {
			delete(deletedTag(template.getQualifiedTag()));
		}
		messages.updateTemplate(template);
	}

	@Timed(value = "jasper.template", histogram = true)
	public void delete(String qualifiedTag) {
		if (archive) {
			archiveDelete(qualifiedTag);
			return;
		}
		templateRepository.deleteByQualifiedTag(qualifiedTag);
		messages.deleteTemplate(qualifiedTag);
	}

	/**
	 * The current version, or empty if it is missing. In archive mode a blank current version
	 * is a tombstone and is treated as missing.
	 */
	public Optional<Template> current(String qualifiedTag) {
		var maybeExisting = templateRepository.findOneByQualifiedTag(qualifiedTag);
		if (!archive) return maybeExisting;
		return maybeExisting.filter(e -> !isBlank(e));
	}

	/**
	 * Deleting appends a blank version (tombstone). Deleting a tombstone or a deletor tag
	 * prunes every version of the tag and its deletor tag.
	 */
	private void archiveDelete(String qualifiedTag) {
		var maybeExisting = templateRepository.findOneByQualifiedTag(qualifiedTag);
		if (maybeExisting.isEmpty()) return;
		if (isDeletorTag(qualifiedTag) || isBlank(maybeExisting.get())) {
			var startedAt = Instant.now(ensureUniqueModifiedClock);
			var tag = isDeletorTag(qualifiedTag) ? deletedTag(qualifiedTag) : qualifiedTag;
			var deletor = isDeletorTag(qualifiedTag) ? qualifiedTag : deletorTag(qualifiedTag);
			templateRepository.deleteByQualifiedTagAndModifiedLessThanEqual(tag, startedAt);
			templateRepository.deleteByQualifiedTagAndModifiedLessThanEqual(deletor, startedAt);
			return;
		}
		var tombstone = new Template();
		tombstone.setTag(localTag(qualifiedTag));
		tombstone.setOrigin(tagOrigin(qualifiedTag));
		ensureCreateUniqueModified(tombstone);
		messages.deleteTemplate(qualifiedTag);
	}

	void ensureCreateUniqueModified(Template template) {
		ensureCreateUniqueModified(template, false);
	}

	/**
	 * @param create in archive mode, fail if a current version exists
	 */
	void ensureCreateUniqueModified(Template template, boolean create) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					if (archive) {
						// The primary key includes modified, so lock and check the current version before appending
						// A deletor tag also locks and checks the tag it deletes, always locked first
						if (isDeletorTag(template.getTag())) Archive.lock(em, "template", deletedTag(template.getTag()), template.getOrigin());
						Archive.lock(em, "template", template.getTag(), template.getOrigin());
						if (create && current(template.getQualifiedTag()).isPresent()) throw new AlreadyExistsException();
						if (create && isDeletorTag(template.getTag()) && current(deletedTag(template.getQualifiedTag())).isPresent()) throw new AlreadyExistsException();
						template.setModified(nextModified(Instant.now(ensureUniqueModifiedClock), templateRepository.getCursor(template.getOrigin())));
					} else {
						template.setModified(Instant.now(ensureUniqueModifiedClock));
					}
					em.persist(template);
					em.flush();
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (isPkViolation(e, "template")) throw new AlreadyExistsException();
				if (isUniqueModifiedOriginViolation(e, "template")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
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
					if (archive) {
						// Append a new version instead of overwriting the current one
						// Appending does not conflict with the current version, so lock before checking the cursor
						Archive.lock(em, "template", template.getTag(), template.getOrigin());
						if (templateRepository.findOneByQualifiedTag(template.getQualifiedTag())
							.filter(e -> e.getModified().equals(cursor))
							.isEmpty()) throw new ModifiedException("Template");
						template.setModified(nextModified(Instant.now(ensureUniqueModifiedClock), templateRepository.getCursor(template.getOrigin())));
						em.persist(template);
						em.flush();
						return null;
					}
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
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (isUniqueModifiedOriginViolation(e, "template")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
				}
				throw e;
			}
		}
	}

}
