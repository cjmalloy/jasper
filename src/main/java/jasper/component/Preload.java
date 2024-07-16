package jasper.component;

import io.micrometer.core.annotation.Counted;
import jasper.config.Props;
import jasper.domain.*;
import jasper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
		for (var zip : storage.get().listStorage(props.getLocalOrigin(), PRELOAD)) {
			if (!zip.toLowerCase().endsWith(".zip")) continue;
			loadStatic(props.getLocalOrigin(), zip);
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
			backup.restoreRepo(refRepository, props.getLocalOrigin(), zipped.in("/ref.json"), Ref.class);
			backup.restoreRepo(extRepository, props.getLocalOrigin(), zipped.in("/ext.json"), Ext.class);
			backup.restoreRepo(userRepository, props.getLocalOrigin(), zipped.in("/user.json"), User.class);
			backup.restoreRepo(pluginRepository, props.getLocalOrigin(), zipped.in("/plugin.json"), Plugin.class);
			backup.restoreRepo(templateRepository, props.getLocalOrigin(), zipped.in("/template.json"), Template.class);
		} catch (Throwable e) {
			logger.error("{} Error preloading {}", origin, id, e);
		}
		logger.info("{} Finished Preload in {}", origin, Duration.between(start, Instant.now()));
    }
}
