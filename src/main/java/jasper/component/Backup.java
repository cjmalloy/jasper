package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.config.ApplicationProperties;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.errors.NotFoundException;
import jasper.plugin.Feed;
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

@Profile("storage")
@Component
public class Backup {
	private final Logger log = LoggerFactory.getLogger(Backup.class);

	@Autowired
	ApplicationProperties applicationProperties;
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
	public void createBackup(String id, BackupOptionsDto options) throws IOException {
		var start = Instant.now();
		log.info("Creating Backup");
		Files.createDirectories(dir());
		try (FileSystem zipfs = FileSystems.newFileSystem(zipfs("_" + id), Map.of("create", "true"))) {
			if (options == null || options.isRef()) {
				backupRepo(refRepository, zipfs.getPath("/ref.json"), false);
			}
			if (options == null || options.isExt()) {
				backupRepo(extRepository, zipfs.getPath("/ext.json"));
			}
			if (options == null || options.isUser()) {
				backupRepo(userRepository, zipfs.getPath("/user.json"));
			}
			if (options == null || options.isPlugin()) {
				backupRepo(pluginRepository, zipfs.getPath("/plugin.json"));
			}
			if (options == null || options.isTemplate()) {
				backupRepo(templateRepository, zipfs.getPath("/template.json"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// Remove underscore to indicate writing has finished
		Files.move(path("_" + id), path(id));
		log.info("Finished Backup");
		log.info("Backup Duration {}", Duration.between(start, Instant.now()));
	}

	private void backupRepo(StreamMixin<?> repo, Path path) throws IOException {
		backupRepo(repo, path, true);
	}

	private void backupRepo(StreamMixin<?> repo, Path path, boolean evict) throws IOException {
		log.debug("Backing up {}", path.toString());
		var firstElementProcessed = new AtomicBoolean(false);
		Files.write(path, "[".getBytes(), StandardOpenOption.CREATE);
		var buf = new StringBuilder();
		var buffSize = 1000000;
		repo.streamAllByOrderByModifiedDesc().forEach(entity -> {
			try {
				if (firstElementProcessed.getAndSet(true)) {
					buf.append(",\n");
				}
				buf.append(objectMapper.writeValueAsString(entity));
				if (buf.length() > buffSize) {
					Files.write(path, buf.toString().getBytes(), StandardOpenOption.APPEND);
					buf.setLength(0);
				}
				log.debug("Buffer size {}", buf.length());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			if (evict) {
				entityManager.detach(entity);
			}
		});
		if (buf.length() > 0) {
			log.debug("Buffer size {}", buf.length());
			Files.write(path, buf.toString().getBytes(), StandardOpenOption.APPEND);
			buf.setLength(0);
		}
		Files.write(path, "]\n".getBytes(), StandardOpenOption.APPEND);
	}

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
	public void restore(String id, BackupOptionsDto options) {
		var start = Instant.now();
		log.info("Restoring Backup");
		try (FileSystem zipfs = FileSystems.newFileSystem(path(id))) {
			if (options == null || options.isRef()) {
				restoreRepo(refRepository, zipfs.getPath("/ref.json"), Ref.class);
				upgradeFeed(zipfs.getPath("/feed.json"));
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
		log.info("Finished Restore");
		log.info("Restore Duration {}", Duration.between(start, Instant.now()));
	}

	private <T> void restoreRepo(JpaRepository<T, ?> repo, Path path, Class<T> type) {
		try {
			new JsonArrayStreamDataSupplier<>(Files.newInputStream(path), type, objectMapper)
				.forEachRemaining(repo::save);
		} catch (IOException e) {
			// Backup not present in zip, silently skip
		}
	}

	private void upgradeFeed(Path path) {
		try {
			new JsonArrayStreamDataSupplier<>(Files.newInputStream(path), OldFeed.class, objectMapper)
				.forEachRemaining(oldFeed -> {
					var ref = new Ref();
					ref.setUrl(oldFeed.url);
					ref.setOrigin(oldFeed.origin);
					ref.setTitle(oldFeed.name);
					ref.setTags(List.of("public", "internal", "+plugin/feed"));
					ref.setCreated(oldFeed.modified);
					ref.setModified(oldFeed.modified);
					ref.setPublished(oldFeed.modified);
					var feed = new Feed();
					feed.setAddTags(oldFeed.tags);
					feed.setLastScrape(oldFeed.lastScrape);
					feed.setScrapeInterval(oldFeed.scrapeInterval);
					feed.setScrapeDescription(oldFeed.scrapeDescription);
					feed.setRemoveDescriptionIndent(oldFeed.removeDescriptionIndent);
					if (ref.getPlugins() == null) {
						ref.setPlugins(objectMapper.getNodeFactory().objectNode());
					}
					ref.getPlugins().set("+plugin/feed", objectMapper.convertValue(feed, ObjectNode.class));
					refRepository.save(ref);
				});
		} catch (IOException e) {
			// Ignore missing file
		}
	}

	public void store(String id, byte[] zipFile) throws IOException {
		var path = path(id);
		Files.createDirectories(path.getParent());
		Files.write(path, zipFile, StandardOpenOption.CREATE);
	}

	public void delete(String id) throws IOException {
		Files.delete(path(id));
	}

	Path dir() {
		return Paths.get(applicationProperties.getStorage(), "backups");
	}

	Path path(String id) {
		return Paths.get(applicationProperties.getStorage(), "backups", id + ".zip");
	}

	URI zipfs(String id) {
		return URI.create("jar:file:" + applicationProperties.getStorage() + "/backups/" + id + ".zip");
	}

	public static class OldFeed {
		public String url;
		public String origin;
		public String name;
		public List<String> tags;
		public Instant modified;
		public Instant lastScrape;
		public Duration scrapeInterval;
		public boolean scrapeDescription;
		public boolean removeDescriptionIndent;
	}
}
