package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.component.Meta;
import jasper.config.Props;
import jasper.repository.RefRepository;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
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

	public boolean dirty = true;

	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		if (message.getPayload().getMetadata().isCascade()) dirty = true;
	}

	@Scheduled(fixedDelay = 15, initialDelay = 15, timeUnit = TimeUnit.MINUTES)
	public void scheduleCascade() {
		dirty = true;
	}

	@Scheduled(fixedDelay = 5, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
	public void cascade() {
		if (!dirty) return;
		dirty = false;
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
			meta.cascade(origin, ref);
			if (refRepository.clearCascade(ref.getUrl(), ref.getOrigin(), ref.getModified()) == 0) {
				logger.debug("{} Ref changed while cascading, retrying: {}", origin, ref.getUrl());
			}
		}
	}
}
