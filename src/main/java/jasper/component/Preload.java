package jasper.component;

import io.micrometer.core.annotation.Counted;
import jakarta.annotation.PostConstruct;
import jasper.config.Props;
import jasper.domain.*;
import jasper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
		for (var zip : storage.get().listStorage(props.getOrigin(), PRELOAD)) {
			if (!zip.id().toLowerCase().endsWith(".zip")) continue;
			loadStatic(props.getOrigin(), zip.id());
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
