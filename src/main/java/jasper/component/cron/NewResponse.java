package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.Messages;
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

import static java.util.Objects.requireNonNullElse;

/**
 * Updates the Metadata of sources marked with newResponse.
 * Only the first sources of a Ref are updated synchronously, the rest are updated here.
 */
@Profile("!no-new-response")
@Component
public class NewResponse {
	private static final Logger logger = LoggerFactory.getLogger(NewResponse.class);

	@Autowired
	Props props;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Meta meta;

	@Autowired
	Messages messages;

	@Scheduled(fixedDelay = 5, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
	public void newResponse() {
		if (!configs.root().script("+plugin/new-response")) return;
		for (var origin : configs.root().scriptOrigins("+plugin/new-response")) {
			newResponseOrigin(origin);
		}
	}

	void newResponseOrigin(String origin) {
		if (!configs.root().script("+plugin/new-response", origin)) return;
		for (var i = 0; i < props.getNewResponseBatchSize(); i++) {
			var ref = refRepository.getRefNewResponse(origin).orElse(null);
			if (ref == null) return;
			logger.trace("{} Updating new responses for ref ({}) {}: {}",
				origin, ref.getOrigin(), ref.getTitle(), ref.getUrl());
			var modified = requireNonNullElse(ref.getMetadata().getModified(), "");
			meta.newResponse(origin, ref);
			try {
				if (refRepository.updateMetadataIfUnmodified(ref.getUrl(), ref.getOrigin(), modified, ref.getMetadata()) == 0) {
					logger.debug("{} Metadata changed while updating new responses, retrying: {}", origin, ref.getUrl());
					continue;
				}
				messages.updateMetadata(ref);
			} catch (Exception e) {
				logger.error("{} Error updating new responses: {}", origin, ref.getUrl(), e);
				return;
			}
		}
	}
}
