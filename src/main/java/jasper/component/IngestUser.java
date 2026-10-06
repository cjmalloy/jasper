package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import jasper.config.Props;
import jasper.domain.User;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.InvalidPushException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.UserRepository;
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
import static jasper.util.DbConstraint.isPkViolation;
import static jasper.util.DbConstraint.isUniqueModifiedOriginViolation;


@Component
public class IngestUser {
	private static final Logger logger = LoggerFactory.getLogger(IngestUser.class);

	@Autowired
	Props props;

	@Autowired
	UserRepository userRepository;

	@Autowired
	EntityManager em;

	@Autowired
	Messages messages;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Value("#{environment.matchesProfiles('archive')}")
	boolean archive;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.user", histogram = true)
	public void create(User user) {
		if (archive) {
			// The primary key includes modified, so check the current version
			if (current(user.getQualifiedTag()).isPresent()) throw new AlreadyExistsException();
			if (isDeletorTag(user.getTag()) && current(deletedTag(user.getQualifiedTag())).isPresent()) throw new AlreadyExistsException();
		} else if (isDeletorTag(user.getTag())) {
			if (userRepository.existsByQualifiedTag(deletedTag(user.getQualifiedTag()))) throw new AlreadyExistsException();
		} else {
			delete(deletorTag(user.getQualifiedTag()));
		}
		ensureCreateUniqueModified(user);
		messages.updateUser(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void update(User user) {
		if (archive ? current(user.getQualifiedTag()).isEmpty() : !userRepository.existsByQualifiedTag(user.getQualifiedTag())) throw new NotFoundException("User");
		ensureUpdateUniqueModified(user);
		messages.updateUser(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void push(User user) {
		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
			if (e instanceof EntityExistsException) throw new AlreadyExistsException();
			if (isPkViolation(e, "users")) throw new AlreadyExistsException();
			if (isUniqueModifiedOriginViolation(e, "users")) throw new DuplicateModifiedDateException();
			throw e;
		} catch (TransactionSystemException e) {
			if (e.getCause() instanceof RollbackException r) {
				if (r.getCause() instanceof jakarta.validation.ConstraintViolationException) throw new InvalidPushException();
			}
			throw e;
		}
		// In archive mode pushes never remove rows
		if (!archive && isDeletorTag(user.getTag())) {
			delete(deletedTag(user.getQualifiedTag()));
		}
		messages.updateUser(user);
	}

	@Timed(value = "jasper.user", histogram = true)
	public void delete(String qualifiedTag) {
		if (archive) {
			archiveDelete(qualifiedTag);
			return;
		}
		userRepository.deleteByQualifiedTag(qualifiedTag);
		messages.deleteUser(qualifiedTag);
	}

	/**
	 * The current version, or empty if it is missing. In archive mode a blank current version
	 * is a tombstone and is treated as missing.
	 */
	public Optional<User> current(String qualifiedTag) {
		var maybeExisting = userRepository.findOneByQualifiedTag(qualifiedTag);
		if (!archive) return maybeExisting;
		return maybeExisting.filter(e -> !isBlank(e));
	}

	/**
	 * Deleting appends a blank version (tombstone). Deleting a tombstone or a deletor tag
	 * prunes every version of the tag and its deletor tag.
	 */
	private void archiveDelete(String qualifiedTag) {
		var maybeExisting = userRepository.findOneByQualifiedTag(qualifiedTag);
		if (maybeExisting.isEmpty()) return;
		if (isDeletorTag(qualifiedTag) || isBlank(maybeExisting.get())) {
			var startedAt = Instant.now(ensureUniqueModifiedClock);
			var tag = isDeletorTag(qualifiedTag) ? deletedTag(qualifiedTag) : qualifiedTag;
			var deletor = isDeletorTag(qualifiedTag) ? qualifiedTag : deletorTag(qualifiedTag);
			userRepository.deleteByQualifiedTagAndModifiedLessThanEqual(tag, startedAt);
			userRepository.deleteByQualifiedTagAndModifiedLessThanEqual(deletor, startedAt);
			messages.invalidateUser(tag);
			return;
		}
		var tombstone = new User();
		tombstone.setTag(localTag(qualifiedTag));
		tombstone.setOrigin(tagOrigin(qualifiedTag));
		ensureCreateUniqueModified(tombstone);
		messages.deleteUser(qualifiedTag);
	}

	void ensureCreateUniqueModified(User user) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					user.setModified(Instant.now(ensureUniqueModifiedClock));
					em.persist(user);
					em.flush();
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (isPkViolation(e, "users")) throw new AlreadyExistsException();
				if (isUniqueModifiedOriginViolation(e, "users")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
				}
				throw e;
			}
		}
	}

	void ensureUpdateUniqueModified(User user) {
		var cursor = user.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					user.setModified(Instant.now(ensureUniqueModifiedClock));
					if (archive) {
						// Append a new version instead of overwriting the current one
						// Appending does not conflict with the current version, so lock before checking the cursor
						Archive.lock(em, "users", user.getTag(), user.getOrigin());
						if (userRepository.findOneByQualifiedTag(user.getQualifiedTag())
							.filter(e -> e.getModified().equals(cursor))
							.isEmpty()) throw new ModifiedException("User");
						em.persist(user);
						em.flush();
						return null;
					}
					var updated = userRepository.optimisticUpdate(
						cursor,
						user.getTag(),
						user.getOrigin(),
						user.getName(),
						user.getRole(),
						user.getReadAccess(),
						user.getWriteAccess(),
						user.getTagReadAccess(),
						user.getTagWriteAccess(),
						user.getModified(),
						user.getKey(),
						user.getPubKey(),
						user.getAuthorizedKeys(),
						user.getExternal());
					if (updated == 0) {
						throw new ModifiedException("User");
					}
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (isUniqueModifiedOriginViolation(e, "users")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
				}
				throw e;
			}
		}
	}

}
