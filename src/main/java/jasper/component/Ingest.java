package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import jasper.config.Props;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.InvalidPushException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static jasper.component.Meta.expandTags;
import static jasper.util.Archive.isBlank;
import static jasper.util.DbConstraint.isPkViolation;
import static jasper.util.DbConstraint.isUniqueModifiedOriginViolation;

@Component
public class Ingest {
	private static final Logger logger = LoggerFactory.getLogger(Ingest.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

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

	@Value("#{environment.matchesProfiles('archive')}")
	boolean archive;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.ref", histogram = true)
	public void create(String rootOrigin, Ref ref) {
		// In archive mode the primary key includes modified, so check the current version
		if (archive && current(ref.getUrl(), ref.getOrigin()).isPresent()) throw new AlreadyExistsException();
		ref.setCreated(Instant.now());
		validate.ref(rootOrigin, ref);
		rng.update(rootOrigin, ref, null);
		meta.ref(rootOrigin, ref);
		ensureCreateUniqueModified(ref);
		meta.sources(rootOrigin, ref, null, true);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void update(String rootOrigin, Ref ref) {
		update(rootOrigin, ref, false);
	}

	/**
	 * @param syncSources update all sources synchronously instead of deferring to the cascade
	 */
	@Timed(value = "jasper.ref", histogram = true)
	public void update(String rootOrigin, Ref ref, boolean syncSources) {
		var maybeExisting = current(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		validate.ref(rootOrigin, ref);
		rng.update(rootOrigin, ref, maybeExisting.get());
		meta.update(rootOrigin, ref, maybeExisting.get());
		ensureUpdateUniqueModified(ref);
		meta.sources(rootOrigin, ref, maybeExisting.get(), syncSources);
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void updateResponse(String rootOrigin, Ref ref) {
		var maybeExisting = current(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		validate.response(rootOrigin, ref);
		rng.update(rootOrigin, ref, maybeExisting.get());
		meta.response(rootOrigin, ref);
		ensureUpdateUniqueModified(ref);
		meta.responseSource(rootOrigin, ref, maybeExisting.get());
		messages.updateRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void silent(String rootOrigin, Ref ref) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) {
			meta.ref(rootOrigin, ref);
		} else {
			meta.update(rootOrigin, ref, maybeExisting.get());
		}
		ensureSilentUniqueModified(ref);
		meta.sources(rootOrigin, ref, maybeExisting.orElse(null));
		messages.updateSilentRef(ref);
	}

	@Timed(value = "jasper.ref", histogram = true)
	public void push(String rootOrigin, Ref ref, boolean validation, boolean stripInvalidPlugins) {
		var generateMetadata = ref.getModified() == null || ref.getModified().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES));
		if (validation) validate.ref(rootOrigin, ref, stripInvalidPlugins);
		Ref maybeExisting = null;
		if (generateMetadata) {
			maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin()).orElse(null);
			rng.update(rootOrigin, ref, maybeExisting);
			if (maybeExisting != null) {
				meta.update(rootOrigin, ref, maybeExisting);
			} else {
				meta.ref(rootOrigin, ref);
			}
		} else {
			ref.setMetadata(Metadata
				.builder()
				.modified(null)
				.regen(true)
				.expandedTags(expandTags(ref.getTags()))
				.build());
		}
		pushUniqueModified(ref);
		if (generateMetadata) meta.sources(rootOrigin, ref, maybeExisting);
		messages.updateRef(ref);
	}

	@Transactional
	@Timed(value = "jasper.ref", histogram = true)
	public void delete(String rootOrigin, String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return;
		if (archive) {
			archiveDelete(rootOrigin, maybeExisting.get());
			return;
		}
		messages.deleteRef(maybeExisting.get());
		refRepository.deleteByUrlAndOrigin(url, origin);
		meta.sources(rootOrigin, null, maybeExisting.get());
	}

	/**
	 * The current version, or empty if it is missing. In archive mode a blank current version
	 * is a tombstone and is treated as missing.
	 */
	public Optional<Ref> current(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (!archive) return maybeExisting;
		return maybeExisting.filter(r -> !isBlank(r));
	}

	/**
	 * In archive mode a blank version is a tombstone and is treated as missing.
	 */
	public boolean isTombstone(Ref ref) {
		return archive && isBlank(ref);
	}

	/**
	 * Deleting appends a blank version (tombstone). Deleting a tombstone prunes every version.
	 */
	private void archiveDelete(String rootOrigin, Ref existing) {
		// Only metadata of older versions is updated, never flush a stale copy
		em.detach(existing);
		if (isBlank(existing)) {
			var startedAt = Instant.now(ensureUniqueModifiedClock);
			refRepository.deleteByUrlAndOriginAndModifiedLessThanEqual(existing.getUrl(), existing.getOrigin(), startedAt);
			meta.sources(rootOrigin, null, existing);
			return;
		}
		var tombstone = new Ref();
		tombstone.setUrl(existing.getUrl());
		tombstone.setOrigin(existing.getOrigin());
		meta.ref(rootOrigin, tombstone);
		ensureCreateUniqueModified(tombstone);
		meta.sources(rootOrigin, tombstone, existing);
		messages.deleteRef(existing);
	}

	void ensureCreateUniqueModified(Ref ref) {
		var count = 0;
		while (true) {
			try {
				count++;
				new TransactionTemplate(transactionManager).execute(status -> {
					ref.setModified(Instant.now(ensureUniqueModifiedClock));
					em.persist(ref);
					em.flush();
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (isPkViolation(e, "ref")) throw new AlreadyExistsException();
				if (isUniqueModifiedOriginViolation(e, "ref")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
				}
				throw e;
			}
		}
	}

	void ensureSilentUniqueModified(Ref ref) {
		var cursor = ref.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				new TransactionTemplate(transactionManager).execute(status -> {
					refRepository.saveAndFlush(ref);
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (!isUniqueModifiedOriginViolation(e, "ref")) throw e;
				if (count > props.getIngestMaxRetry()) {
					count = 0;
					cursor = cursor.minusNanos((long) (1000 * Math.random()));
					ref.setModified(cursor);
				} else {
					ref.setModified(ref.getModified().minusMillis(1));
				}
			}
		}
	}

	void ensureUpdateUniqueModified(Ref ref) {
		var cursor = ref.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				new TransactionTemplate(transactionManager).execute(status -> {
					ref.setModified(Instant.now(ensureUniqueModifiedClock));
					if (archive) {
						// Append a new version instead of overwriting the current one
						// Appending does not conflict with the current version, so lock before checking the cursor
						Archive.lock(em, "ref", ref.getUrl(), ref.getOrigin());
						var current = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin())
							.filter(r -> r.getModified().equals(cursor))
							.orElseThrow(() -> new ModifiedException("Ref"));
						ref.setCreated(current.getCreated());
						em.persist(ref);
						em.flush();
						return null;
					}
					var updated = refRepository.optimisticUpdate(
						cursor,
						ref.getUrl(),
						ref.getOrigin(),
						ref.getTitle(),
						ref.getComment(),
						ref.getTags(),
						ref.getSources(),
						ref.getAlternateUrls(),
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
			} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
				if (isUniqueModifiedOriginViolation(e, "ref")) {
					if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
					continue;
				}
				throw e;
			}
		}
	}

	void pushUniqueModified(Ref ref) {
		try {
			int updated;
			if (archive) {
				// Only update a repeated push of the same version, otherwise insert a new version
				updated = refRepository.pushAsyncMetadataVersion(
					ref.getUrl(),
					ref.getOrigin(),
					ref.getTitle(),
					ref.getComment(),
					ref.getTags(),
					ref.getSources(),
					ref.getAlternateUrls(),
					ref.getPlugins(),
					ref.getMetadata(),
					ref.getPublished(),
					ref.getModified());
			} else {
				updated = refRepository.pushAsyncMetadata(
					ref.getUrl(),
					ref.getOrigin(),
					ref.getTitle(),
					ref.getComment(),
					ref.getTags(),
					ref.getSources(),
					ref.getAlternateUrls(),
					ref.getPlugins(),
					ref.getMetadata(),
					ref.getPublished(),
					ref.getModified());
			}
			if (updated == 0) {
				refRepository.save(ref);
			}
		} catch (DataIntegrityViolationException | PersistenceException | JpaSystemException e) {
			if (e instanceof EntityExistsException) throw new AlreadyExistsException();
			if (isPkViolation(e, "ref")) throw new AlreadyExistsException();
			if (isUniqueModifiedOriginViolation(e, "ref")) throw new DuplicateModifiedDateException();
			throw e;
		} catch (TransactionSystemException e) {
			if (e.getCause() instanceof RollbackException r) {
				if (r.getCause() instanceof jakarta.validation.ConstraintViolationException) throw new InvalidPushException();
			}
			throw e;
		}
	}

}
