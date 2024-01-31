package jasper.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.Cursor;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.StreamMixin;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.service.dto.BackupOptionsDto;
import jasper.util.JsonArrayStreamDataSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.domain.proj.HasOrigin.isSubOrigin;

@Profile("storage")
@Component
public class Backup {
	private final Logger logger = LoggerFactory.getLogger(Backup.class);
	private static final String BACKUPS = "backups";

	@Autowired
	Props props;
	@Autowired
	RefRepository refRepository;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	TemplateRepository templateRepository;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	EntityManager entityManager;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	Storage storage;

	@Async
	@Transactional(readOnly = true)
	@Counted(value = "jasper.backup")
	public void createBackup(String origin, String id, BackupOptionsDto options) throws IOException {
		var start = Instant.now();
		logger.info("Creating Backup");
		try (var zipped = storage.zipAt(origin, BACKUPS, id + ".zip")) {
			if (options.isRef()) {
				backupRepo(refRepository, origin, options.getNewerThan(), zipped.out("/ref.json"), false);
			}
			if (options.isExt()) {
				backupRepo(extRepository, origin, options.getNewerThan(), zipped.out("/ext.json"));
			}
			if (options.isUser()) {
				backupRepo(userRepository, origin, options.getNewerThan(), zipped.out("/user.json"));
			}
			if (options.isPlugin()) {
				backupRepo(pluginRepository, origin, options.getNewerThan(), zipped.out("/plugin.json"));
			}
			if (options.isTemplate()) {
				backupRepo(templateRepository, origin, options.getNewerThan(), zipped.out("/template.json"));
			}
		}
		logger.info("Finished Backup");
		logger.info("Backup Duration {}", Duration.between(start, Instant.now()));
	}

	private void backupRepo(StreamMixin<?> repo, String origin, Instant newerThan, OutputStream out) throws IOException {
		backupRepo(repo, origin, newerThan, out, true);
	}

	private void backupRepo(StreamMixin<?> repo, String origin, Instant newerThan, OutputStream out, boolean evict) throws IOException {
		try (out) {
			var firstElementProcessed = new AtomicBoolean(false);
			var buf = new StringBuilder();
			buf.append("[");
			var buffSize = props.getBackupBufferSize();
			Stream<?> stream;
			if (newerThan != null) {
				stream = repo.streamAllByOriginAndModifiedGreaterThanEqualOrderByModifiedDesc(origin, newerThan);
			} else {
				stream = repo.streamAllByOriginOrderByModifiedDesc(origin);
			}
			stream.forEach(entity -> {
				try {
					if (firstElementProcessed.getAndSet(true)) {
						buf.append(",\n");
					}
					buf.append(objectMapper.writeValueAsString(entity));
					if (buf.length() > buffSize) {
						logger.debug("Flushing buffer {} bytes", buf.length());
						StreamUtils.copy(buf.toString().getBytes(), out);
						buf.setLength(0);
						entityManager.clear();
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				if (evict) {
					entityManager.detach(entity);
				}
			});
			buf.append("]");
			logger.debug("Flushing buffer {} bytes", buf.length());
			StreamUtils.copy(buf.toString().getBytes(), out);
		}
	}

	@Timed(value = "jasper.backup", histogram = true)
	public byte[] get(String origin, String id) {
		return storage.get(origin, BACKUPS, id + ".zip");
	}

	public boolean exists(String origin, String id) {
		return storage.exists(origin, BACKUPS, id + ".zip");
	}

	public List<String> listBackups(String origin) {
		return storage.listStorage(origin, BACKUPS).stream()
			.filter(n -> n.endsWith(".zip")).toList();
	}

	@Async
	@Counted(value = "jasper.backup")
	public void restore(String origin, String id, BackupOptionsDto options) {
		var start = Instant.now();
		logger.info("Restoring Backup");
		try (var zipped = storage.streamZip(origin, BACKUPS, id + ".zip")) {
			if (options == null || options.isRef()) {
				restoreRepo(refRepository, origin, zipped.in("/ref.json"), Ref.class);
			}
			if (options == null || options.isExt()) {
				restoreRepo(extRepository, origin, zipped.in("/ext.json"), Ext.class);
			}
			if (options == null || options.isUser()) {
				restoreRepo(userRepository, origin, zipped.in("/user.json"), User.class);
			}
			if (options == null || options.isPlugin()) {
				restoreRepo(pluginRepository, origin, zipped.in("/plugin.json"), Plugin.class);
			}
			if (options == null || options.isTemplate()) {
				restoreRepo(templateRepository, origin, zipped.in("/template.json"), Template.class);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		logger.info("Finished Restore");
		logger.info("Restore Duration {}", Duration.between(start, Instant.now()));
	}

	private <T extends Cursor> void restoreRepo(JpaRepository<T, ?> repo, String origin, InputStream file, Class<T> type) {
		if (file == null) return; // Silently ignore missing files
		AtomicBoolean done = new AtomicBoolean(false);
		var it = new JsonArrayStreamDataSupplier<>(file, type, objectMapper);
		int count = 0;
		try {
			while (!done.get()) {
				TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
				transactionTemplate.execute(status -> {
					for (var i = 0; i < this.props.getRestoreBatchSize(); i++) {
						if (!it.hasNext()) {
							done.set(true);
							break;
						}
						var t = it.next();
						try {
							if (!isSubOrigin(origin, t.getOrigin())) t.setOrigin(origin);
							repo.save(t);
						} catch (Exception e) {
							try {
								logger.error("{} Skipping {} {} due to constraint violation", origin, type.getSimpleName(), objectMapper.writeValueAsString(t), e);
							} catch (JsonProcessingException ex) {
								logger.error("{} Skipping {} {} due to constraint violation", origin, type.getSimpleName(), type, e);
							}
						}
					}
					return null;
				});
				count++;
				logger.info("{} {} {} restored...", origin, type.getSimpleName(), count * this.props.getRestoreBatchSize());
			}
		} catch (Exception e) {
			logger.error("Failed to restore", e);
		}
    }

	@Timed(value = "jasper.backup", histogram = true)
	public void store(String origin, String id, InputStream zipFile) throws IOException {
		storage.storeAt(origin, BACKUPS, id+ ".zip", zipFile);
	}

	/**
	 * Will first drop and then regenerate all metadata
	 * for the given origin and all sub-origins.
	 */
	@Async
	@Counted(value = "jasper.backup")
	public void backfill(String origin) {
		var start = Instant.now();
		logger.info("{} Starting Backfill", formatOrigin(origin));
		refRepository.dropMetadata(origin);
		logger.info("{} Cleared old metadata", formatOrigin(origin));
		int count = 0;
		while (props.getBackfillBatchSize() == refRepository.backfillMetadata(origin, props.getBackfillBatchSize())) {
			count += props.getBackfillBatchSize();
			logger.info("{} Generating metadata... {} done", formatOrigin(origin), count);
		}
		logger.info("{} Finished Backfill", formatOrigin(origin));
		logger.info("{} Backfill Duration {}", formatOrigin(origin), Duration.between(start, Instant.now()));
	}

	@Timed(value = "jasper.backup", histogram = true)
	public void delete(String origin, String id) throws IOException {
		storage.delete(origin, BACKUPS, id + ".zip");
	}
}
