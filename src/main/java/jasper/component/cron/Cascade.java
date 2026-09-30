package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.Meta;
import jasper.config.Props;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Updates the Metadata of the sources of Refs marked for cascade.
 * Only the first sources of a Ref are updated synchronously, the rest are updated here.
 */
@Profile("!no-cascade")
@Component
public class Cascade {
	private static final Logger logger = LoggerFactory.getLogger(Cascade.class);

	@Autowired
	Props props;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Meta meta;

	@Scheduled(fixedDelay = 5, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
	public void cascade() {
		if (!configs.root().script("+plugin/cascade")) return;
		for (var origin : configs.root().scriptOrigins("+plugin/cascade")) {
			cascadeOrigin(origin);
		}
	}

	void cascadeOrigin(String origin) {
		if (!configs.root().script("+plugin/cascade", origin)) return;
		for (var i = 0; i < props.getCascadeBatchSize(); i++) {
			var ref = refRepository.getRefCascade(origin).orElse(null);
			if (ref == null) return;
			logger.trace("{} Cascading response metadata for ref ({}) {}: {}",
				origin, ref.getOrigin(), ref.getTitle(), ref.getUrl());
			try {
				if (!meta.cascade(origin, ref)) {
					logger.debug("{} Source metadata changed while cascading, retrying: {}", origin, ref.getUrl());
					return;
				}
				if (refRepository.clearCascade(ref.getUrl(), ref.getOrigin(), ref.getModified()) == 0) {
					logger.debug("{} Ref changed while cascading, retrying: {}", origin, ref.getUrl());
				}
			} catch (Exception e) {
				logger.error("{} Error cascading response metadata: {}", origin, ref.getUrl(), e);
				return;
			}
		}
	}
}
