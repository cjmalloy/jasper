package jasper.component;

import io.micrometer.core.annotation.Counted;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jasper.config.Props;
import jasper.domain.*;
import jasper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

@Profile("preload")
@Component
public class Preload {
	private final Logger logger = LoggerFactory.getLogger(Preload.class);
	private static final String PRELOAD = "preload";

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
	Optional<Storage> storage;

	@Autowired
	Backup backup;

	private WatchService watchService;
	private Map<String, Long> loadedFiles = new ConcurrentHashMap<>();

	@PostConstruct
	public void init() {
		if (storage.isEmpty()) {
			logger.error("Preload enabled but no storage present.");
			return;
		}
		// Load all existing zip files on startup
		for (var zip : storage.get().listStorage(props.getOrigin(), PRELOAD)) {
			if (!zip.id().toLowerCase().endsWith(".zip")) continue;
			loadStatic(props.getOrigin(), zip.id());
			loadedFiles.put(zip.id(), zip.size());
		}
		// Set up file watcher
		setupWatcher();
	}

	private void setupWatcher() {
		try {
			Path preloadDir = getPreloadDir();
			if (preloadDir == null || !Files.exists(preloadDir)) {
				logger.info("{} Preload directory does not exist: {}", props.getOrigin(), preloadDir);
				return;
			}
			watchService = FileSystems.getDefault().newWatchService();
			preloadDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
			logger.info("{} Watching preload directory: {}", props.getOrigin(), preloadDir);
		} catch (IOException e) {
			logger.error("{} Failed to setup file watcher", props.getOrigin(), e);
		}
	}

	@PreDestroy
	public void cleanup() {
		if (watchService != null) {
			try {
				watchService.close();
			} catch (IOException e) {
				logger.error("{} Error closing watch service", props.getOrigin(), e);
			}
		}
	}

	@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	public void checkForChanges() {
		if (storage.isEmpty()) return;
		
		// Check watch service for file system events
		if (watchService != null) {
			WatchKey key;
			while ((key = watchService.poll()) != null) {
				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();
					if (kind == OVERFLOW) continue;
					
					@SuppressWarnings("unchecked")
					WatchEvent<Path> ev = (WatchEvent<Path>) event;
					Path filename = ev.context();
					String id = filename.toString();
					
					if (!id.toLowerCase().endsWith(".zip")) continue;
					
					logger.debug("{} File event detected: {} {}", props.getOrigin(), kind.name(), id);
					processFile(id);
				}
				key.reset();
			}
		}
		
		// Fallback: Poll for changes in case watch service fails or isn't available
		for (var zip : storage.get().listStorage(props.getOrigin(), PRELOAD)) {
			if (!zip.id().toLowerCase().endsWith(".zip")) continue;
			processFile(zip.id());
		}
	}

	private void processFile(String id) {
		if (storage.isEmpty()) return;
		
		// Check if file exists
		if (!storage.get().exists(props.getOrigin(), PRELOAD, id)) {
			loadedFiles.remove(id);
			return;
		}
		
		// Check if file is new or has changed size
		long currentSize = storage.get().size(props.getOrigin(), PRELOAD, id);
		Long previousSize = loadedFiles.get(id);
		
		if (previousSize == null || previousSize != currentSize) {
			logger.info("{} Detected {} file: {}", props.getOrigin(), previousSize == null ? "new" : "changed", id);
			loadStatic(props.getOrigin(), id);
			loadedFiles.put(id, currentSize);
		}
	}

	private Path getPreloadDir() {
		if (storage.isEmpty()) return null;
		String tenant = storage.get().originTenant(props.getOrigin());
		return Paths.get(props.getStorage(), tenant, PRELOAD);
	}

	@Counted(value = "jasper.preload")
	public void loadStatic(String origin, String id) {
		if (storage.isEmpty()) {
			logger.error("Error loading static files with no storage present.");
			return;
		}
		var start = Instant.now();
		logger.info("{} Preloading static files {}", origin, id);
		try (var zipped = storage.get().streamZip(origin, PRELOAD, id)) {
			backup.restoreRepo(refRepository, origin, zipped.in("/ref.json"), Ref.class);
			backup.restoreRepo(extRepository, origin, zipped.in("/ext.json"), Ext.class);
			backup.restoreRepo(userRepository, origin, zipped.in("/user.json"), User.class);
			backup.restoreRepo(pluginRepository, origin, zipped.in("/plugin.json"), Plugin.class);
			backup.restoreRepo(templateRepository, origin, zipped.in("/template.json"), Template.class);
		} catch (Throwable e) {
			logger.error("{} Error preloading {}", origin, id, e);
		}
		logger.info("{} Finished Preload in {}", origin, Duration.between(start, Instant.now()));
    }
}
