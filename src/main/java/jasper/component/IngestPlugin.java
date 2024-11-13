package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jasper.config.Props;
import jasper.domain.Plugin;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
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

@Component
public class IngestPlugin {
	private static final Logger logger = LoggerFactory.getLogger(IngestPlugin.class);

	@Autowired
	Props props;

	@Autowired
	PluginRepository pluginRepository;

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

	@Timed(value = "jasper.plugin", histogram = true)
	public void create(Plugin plugin) {
		if (isDeletorTag(plugin.getTag())) {
			if (pluginRepository.existsByQualifiedTag(deletedTag(plugin.getTag()) + plugin.getOrigin())) throw new AlreadyExistsException();
		} else {
			delete(deletorTag(plugin.getTag()) + plugin.getOrigin());
		}
		validate.plugin(plugin.getOrigin(), plugin);
		ensureCreateUniqueModified(plugin);
		messages.updatePlugin(plugin);
	}

	@Timed(value = "jasper.plugin", histogram = true)
	public void update(Plugin plugin) {
		if (!pluginRepository.existsByQualifiedTag(plugin.getTag() + plugin.getOrigin())) throw new NotFoundException("Plugin");
		validate.plugin(plugin.getOrigin(), plugin);
		ensureUpdateUniqueModified(plugin);
		messages.updatePlugin(plugin);
	}

	@Timed(value = "jasper.plugin", histogram = true)
	public void push(Plugin plugin) {
		validate.plugin(plugin.getOrigin(), plugin);
		try {
			pluginRepository.save(plugin);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
		if (isDeletorTag(plugin.getTag())) {
			delete(deletedTag(plugin.getTag()) + plugin.getOrigin());
		}
		messages.updatePlugin(plugin);
	}

	@Timed(value = "jasper.plugin", histogram = true)
	public void delete(String qualifiedTag) {
		pluginRepository.deleteByQualifiedTag(qualifiedTag);
		messages.deletePlugin(qualifiedTag);
	}

	void ensureCreateUniqueModified(Plugin plugin) {
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					plugin.setModified(Instant.now(ensureUniqueModifiedClock));
					em.persist(plugin);
					em.flush();
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e instanceof EntityExistsException) throw new AlreadyExistsException();
				if (e instanceof ConstraintViolationException c) {
					if ("plugin_pkey".equals(c.getConstraintName())) throw new AlreadyExistsException();
					if ("plugin_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("plugin_pkey".equals(c.getConstraintName())) throw new AlreadyExistsException();
					if ("plugin_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

	void ensureUpdateUniqueModified(Plugin plugin) {
		var cursor = plugin.getModified();
		var count = 0;
		while (true) {
			try {
				count++;
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					plugin.setModified(Instant.now(ensureUniqueModifiedClock));
					var updated = pluginRepository.optimisticUpdate(
						cursor,
						plugin.getTag(),
						plugin.getOrigin(),
						plugin.getName(),
						plugin.getConfig(),
						plugin.getSchema(),
						plugin.getDefaults(),
						plugin.isGenerateMetadata(),
						plugin.isUserUrl(),
						plugin.getModified());
					if (updated == 0) {
						throw new ModifiedException("Plugin");
					}
					return null;
				});
				break;
			} catch (DataIntegrityViolationException | PersistenceException e) {
				if (e instanceof ConstraintViolationException c) {
					if ("plugin_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				if (e.getCause() instanceof ConstraintViolationException c) {
					if ("plugin_modified_origin_key".equals(c.getConstraintName())) {
						if (count > props.getIngestMaxRetry()) throw new DuplicateModifiedDateException();
						continue;
					}
				}
				throw e;
			}
		}
	}

}
