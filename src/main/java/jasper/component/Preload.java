package jasper.component;

import io.micrometer.core.annotation.Counted;
import jakarta.annotation.PostConstruct;
import jasper.config.Props;
import jasper.domain.*;
import jasper.domain.proj.Cursor;
import jasper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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

	@PostConstruct
	public void init() {
		if (storage.isEmpty()) {
			logger.error("Preload enabled but no storage present.");
			return;
		}
		for (var file : storage.get().listStorage(props.getOrigin(), PRELOAD)) {
			var filename = file.id().toLowerCase();
			if (filename.endsWith(".zip")) {
				loadStatic(props.getOrigin(), file.id());
			} else if (filename.matches("(ref|ext|user|plugin|template).*\\.json")) {
				loadJsonFile(props.getOrigin(), file.id());
			}
		}
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
			zipped.list("ref.*\\.json").forEachRemaining(is -> backup.restoreRepo(refRepository, origin, is, Ref.class));
			zipped.list("ext.*\\.json").forEachRemaining(is -> backup.restoreRepo(extRepository, origin, is, Ext.class));
			zipped.list("user.*\\.json").forEachRemaining(is -> backup.restoreRepo(userRepository, origin, is, User.class));
			zipped.list("plugin.*\\.json").forEachRemaining(is -> backup.restoreRepo(pluginRepository, origin, is, Plugin.class));
			zipped.list("template.*\\.json").forEachRemaining(is -> backup.restoreRepo(templateRepository, origin, is, Template.class));
		} catch (Throwable e) {
			logger.error("{} Error preloading {}", origin, id, e);
		}
		logger.info("{} Finished Preload in {}", origin, Duration.between(start, Instant.now()));
    }

	@Counted(value = "jasper.preload")
	public void loadJsonFile(String origin, String id) {
		if (storage.isEmpty()) {
			logger.error("Error loading JSON file with no storage present.");
			return;
		}
		var start = Instant.now();
		logger.info("{} Preloading JSON file {}", origin, id);
		try {
			var filename = id.toLowerCase();
			if (filename.matches("ref.*\\.json")) {
				backup.restoreRepo(refRepository, origin, storage.get().stream(origin, PRELOAD, id), Ref.class);
			} else if (filename.matches("ext.*\\.json")) {
				backup.restoreRepo(extRepository, origin, storage.get().stream(origin, PRELOAD, id), Ext.class);
			} else if (filename.matches("user.*\\.json")) {
				backup.restoreRepo(userRepository, origin, storage.get().stream(origin, PRELOAD, id), User.class);
			} else if (filename.matches("plugin.*\\.json")) {
				backup.restoreRepo(pluginRepository, origin, storage.get().stream(origin, PRELOAD, id), Plugin.class);
			} else if (filename.matches("template.*\\.json")) {
				backup.restoreRepo(templateRepository, origin, storage.get().stream(origin, PRELOAD, id), Template.class);
			}
		} catch (Throwable e) {
			logger.error("{} Error preloading JSON file {}", origin, id, e);
		}
		logger.info("{} Finished Preload in {}", origin, Duration.between(start, Instant.now()));
	}
}
