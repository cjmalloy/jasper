package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.InvalidPushException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
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
import static jasper.util.DbConstraint.isPkViolation;
import static jasper.util.DbConstraint.isUniqueModifiedOriginViolation;

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

	@Value("#{environment.matchesProfiles('archive')}")
	boolean archive;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.ext", histogram = true)
	public void create(Ext ext) {
		if (archive) {
			// The primary key includes modified, so check the current version
			if (current(ext.getQualifiedTag()).isPresent()) throw new AlreadyExistsException();
			if (isDeletorTag(ext.getTag()) && current(deletedTag(ext.getQualifiedTag())).isPresent()) throw new AlreadyExistsException();
		} else if (isDeletorTag(ext.getTag())) {
			if (extRepository.existsByQualifiedTag(deletedTag(ext.getQualifiedTag()))) throw new AlreadyExistsException();
		} else {
			delete(deletorTag(ext.getQualifiedTag()));
		}
		validate.ext(ext.getOrigin(), ext);
		ensureCreateUniqueModified(ext);
		messages.updateExt(ext);
	}

	@Timed(value = "jasper.ext", histogram = true)
	public void update(Ext ext) {
		if (archive ? current(ext.getQualifiedTag()).isEmpty() : !extRepository.existsByQualifiedTag(ext.getQualifiedTag())) throw new NotFoundException("Ext");
		validate.ext(ext.getOrigin(), ext);
		ensureUpdateUniqueModified(ext);
		messages.updateExt(ext);
	}

	@Timed(value = "jasper.ext", histogram = true)
	public void push(String rootOrigin, Ext ext, boolean validation, boolean stripInvalidTemplates) {
		if (validation) validate.ext(rootOrigin, ext, stripInvalidTemplates);
		pushUniqueModified(ext);
		// In archive mode pushes never remove rows
		if (!archive) {
			if (isDeletorTag(ext.getTag())) {
				delete(deletedTag(ext.getQualifiedTag()));
			} else {
				delete(deletorTag(ext.getQualifiedTag()));
			}
		}
		messages.updateExt(ext);
	}

	@Timed(value = "jasper.ext", histogram = true)
	public void delete(String qualifiedTag) {
		if (archive) {
			archiveDelete(qualifiedTag);
			return;
		}
		extRepository.deleteByQualifiedTag(qualifiedTag);
	}

	/**
	 * The current version, or empty if it is missing. In archive mode a blank current version
	 * is a tombstone and is treated as missing.
	 */
	public Optional<Ext> current(String qualifiedTag) {
		var maybeExisting = extRepository.findOneByQualifiedTag(qualifiedTag);
		if (!archive) return maybeExisting;
		return maybeExisting.filter(e -> !isBlank(e));
	}

	/**
	 * Deleting appends a blank version (tombstone). Deleting a tombstone or a deletor tag
	 * prunes every version of the tag and its deletor tag.
	 */
	private void archiveDelete(String qualifiedTag) {
		var maybeExisting = extRepository.findOneByQualifiedTag(qualifiedTag);
		if (maybeExisting.isEmpty()) return;
		if (isDeletorTag(qualifiedTag) || isBlank(maybeExisting.get())) {
			var startedAt = Instant.now();
			var tag = isDeletorTag(qualifiedTag) ? deletedTag(qualifiedTag) : qualifiedTag;
			var deletor = isDeletorTag(qualifiedTag) ? qualifiedTag : deletorTag(qualifiedTag);
			extRepository.deleteByQualifiedTagAndModifiedLessThanEqual(tag, startedAt);
			extRepository.deleteByQualifiedTagAndModifiedLessThanEqual(deletor, startedAt);
			return;
		}
		var tombstone = new Ext();
		tombstone.setTag(localTag(qualifiedTag));
		tombstone.setOrigin(tagOrigin(qualifiedTag));
		ensureCreateUniqueModified(tombstone);
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
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (isPkViolation(e, "ext")) throw new AlreadyExistsException();
				if (isUniqueModifiedOriginViolation(e, "ext")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
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
					if (archive) {
						// Append a new version instead of overwriting the current one
						if (extRepository.findOneByQualifiedTag(ext.getQualifiedTag())
							.filter(e -> e.getModified().equals(cursor))
							.isEmpty()) throw new ModifiedException("Ext");
						em.persist(ext);
						em.flush();
						return null;
					}
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
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (isUniqueModifiedOriginViolation(e, "ext")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
				}
				throw e;
			}
		}
	}

	private void pushUniqueModified(Ext ext) {
		try {
			extRepository.save(ext);
		} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
			if (e instanceof EntityExistsException) throw new AlreadyExistsException();
			if (isPkViolation(e, "ext")) throw new AlreadyExistsException();
			if (isUniqueModifiedOriginViolation(e, "ext")) throw new DuplicateModifiedDateException();
			throw e;
		} catch (TransactionSystemException e) {
			if (e.getCause() instanceof RollbackException r) {
				if (r.getCause() instanceof jakarta.validation.ConstraintViolationException) throw new InvalidPushException();
			}
			throw e;
		}
	}

}
