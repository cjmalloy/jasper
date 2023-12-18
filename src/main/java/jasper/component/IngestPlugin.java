package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
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
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

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
	PlatformTransactionManager transactionManager;

	// Exposed for testing
	Clock ensureUniqueModifiedClock = Clock.systemUTC();

	@Timed(value = "jasper.plugin", histogram = true)
	public void create(Plugin plugin) {
		ensureCreateUniqueModified(plugin);
	}

	@Timed(value = "jasper.plugin", histogram = true)
	public void update(Plugin plugin) {
		ensureUpdateUniqueModified(plugin);
	}

	@Timed(value = "jasper.plugin", histogram = true)
	public void push(Plugin plugin) {
		pluginRepository.save(plugin);
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
