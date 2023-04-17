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
import jasper.errors.NotFoundException;
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
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Profile("storage")
@Component
public class Backup {
	private final Logger logger = LoggerFactory.getLogger(Backup.class);

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

	@Async
	@Transactional(readOnly = true)
	@Counted(value = "jasper.backup")
	public void createBackup(String id, BackupOptionsDto options) throws IOException {
		var start = Instant.now();
		logger.info("Creating Backup");
		Files.createDirectories(dir());
		try (FileSystem zipfs = FileSystems.newFileSystem(zipfs("_" + id), Map.of("create", "true"))) {
			if (options.isRef()) {
				backupRepo(refRepository, options.getNewerThan(), zipfs.getPath("/ref.json"), false);
			}
			if (options.isExt()) {
				backupRepo(extRepository, options.getNewerThan(), zipfs.getPath("/ext.json"));
			}
			if (options.isUser()) {
				backupRepo(userRepository, options.getNewerThan(), zipfs.getPath("/user.json"));
			}
			if (options.isPlugin()) {
				backupRepo(pluginRepository, options.getNewerThan(), zipfs.getPath("/plugin.json"));
			}
			if (options.isTemplate()) {
				backupRepo(templateRepository, options.getNewerThan(), zipfs.getPath("/template.json"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// Remove underscore to indicate writing has finished
		Files.move(path("_" + id), path(id));
		logger.info("Finished Backup");
		logger.info("Backup Duration {}", Duration.between(start, Instant.now()));
	}

	private void backupRepo(StreamMixin<?> repo, Instant newerThan, Path path) throws IOException {
		backupRepo(repo, newerThan, path, true);
	}

	private void backupRepo(StreamMixin<?> repo, Instant newerThan, Path path, boolean evict) throws IOException {
		logger.debug("Backing up {}", path.toString());
		var firstElementProcessed = new AtomicBoolean(false);
		Files.write(path, "[".getBytes(), StandardOpenOption.CREATE);
		var buf = new StringBuilder();
		var buffSize = props.getBackupBufferSize();
		Stream<?> stream;
		if (newerThan != null) {
			stream = repo.streamAllByModifiedGreaterThanEqualOrderByModifiedDesc(newerThan);
		} else {
			stream = repo.streamAllByOrderByModifiedDesc();
		}
		stream.forEach(entity -> {
			try {
				if (firstElementProcessed.getAndSet(true)) {
					buf.append(",\n");
				}
				buf.append(objectMapper.writeValueAsString(entity));
				if (buf.length() > buffSize) {
					logger.debug("Flushing buffer {} bytes", buf.length());
					Files.write(path, buf.toString().getBytes(), StandardOpenOption.APPEND);
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
		if (buf.length() > 0) {
			logger.debug("Flushing buffer {} bytes", buf.length());
			Files.write(path, buf.toString().getBytes(), StandardOpenOption.APPEND);
			buf.setLength(0);
		}
		Files.write(path, "]\n".getBytes(), StandardOpenOption.APPEND);
	}

	@Timed(value = "jasper.backup", histogram = true)
	public byte[] get(String id) {
		try {
			return Files.readAllBytes(path(id));
		} catch (IOException e) {
			throw new NotFoundException("Backup " + id);
		}
	}

	public boolean exists(String id) {
		return path(id).toFile().exists();
	}

	public List<String> listBackups() {
		try (var list = Files.list(dir())) {
			return list
				.map(f -> f.getFileName().toString())
				.filter(n -> n.endsWith(".zip"))
				.collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	@Async
	@Counted(value = "jasper.backup")
	public void restore(String id, BackupOptionsDto options) {
		var start = Instant.now();
		logger.info("Restoring Backup");
		try (FileSystem zipfs = FileSystems.newFileSystem(path(id))) {
			if (options == null || options.isRef()) {
				restoreRepo(refRepository, zipfs.getPath("/ref.json"), Ref.class);
			}
			if (options == null || options.isExt()) {
				restoreRepo(extRepository, zipfs.getPath("/ext.json"), Ext.class);
			}
			if (options == null || options.isUser()) {
				restoreRepo(userRepository, zipfs.getPath("/user.json"), User.class);
			}
			if (options == null || options.isPlugin()) {
				restoreRepo(pluginRepository, zipfs.getPath("/plugin.json"), Plugin.class);
			}
			if (options == null || options.isTemplate()) {
				restoreRepo(templateRepository, zipfs.getPath("/template.json"), Template.class);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		logger.info("Finished Restore");
		logger.info("Restore Duration {}", Duration.between(start, Instant.now()));
	}

	private <T> void restoreRepo(JpaRepository<T, ?> repo, Path path, Class<T> type) {
		try {
			new JsonArrayStreamDataSupplier<>(Files.newInputStream(path), type, objectMapper)
				.forEachRemaining(t -> {
					try {
						repo.save(t);
					} catch (Exception e) {
						try {
							logger.error("Skipping {} due to constraint violation", objectMapper.writeValueAsString(t), e);
						} catch (JsonProcessingException ex) {
							logger.error("Skipping {} due to constraint violation", t, e);
						}
					}
				});
		} catch (IOException e) {
			// Backup not present in zip, silently skip
		}
	}

	@Timed(value = "jasper.backup", histogram = true)
	public void store(String id, byte[] zipFile) throws IOException {
		var path = path(id);
		Files.createDirectories(path.getParent());
		Files.write(path, zipFile, StandardOpenOption.CREATE_NEW);
	}

	@Async
	@Transactional
	@Counted(value = "jasper.backfill")
	public void backfill(String validationOrigin) {
		refRepository.backfill(validationOrigin);
	}

	@Timed(value = "jasper.backup", histogram = true)
	public void delete(String id) throws IOException {
		Files.delete(path(id));
	}

	Path dir() {
		return Paths.get(props.getStorage(), "backups");
	}

	Path path(String id) {
		if (id.contains("/") || id.contains("\\")) throw new NotFoundException("Illegal characters");
		return Paths.get(props.getStorage(), "backups", id + ".zip");
	}

	URI zipfs(String id) {
		if (id.contains("/") || id.contains("\\")) throw new NotFoundException("Illegal characters");
		return URI.create("jar:file:" + props.getStorage() + "/backups/" + id + ".zip");
	}
}
