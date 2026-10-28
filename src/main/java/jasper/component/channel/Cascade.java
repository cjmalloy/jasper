package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.component.Meta;
import jasper.config.Props;
import jasper.domain.Ref;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import static jasper.component.Meta.SYNC_SOURCES;
import static jasper.repository.spec.OriginSpec.isUnderOrigin;
import static jasper.repository.spec.RefSpec.isNotObsolete;
import static jasper.repository.spec.RefSpec.isUrl;

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

	@Autowired
	PlatformTransactionManager transactionManager;

	public volatile boolean dirty = true;

	@ServiceActivator(inputChannel = "refRxChannel")
	public void handleRefUpdate(Message<RefDto> message) {
		if (message.getPayload().getMetadata() != null && message.getPayload().getMetadata().isCascade()) {
			dirty = true;
		}
	}

	@Scheduled(fixedDelay = 15, initialDelay = 15, timeUnit = TimeUnit.MINUTES)
	public void scheduleCascade() {
		dirty = true;
	}

	@Scheduled(fixedDelay = 2, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
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
			try {
				cascadeRef(origin, ref);
			} catch (Exception e) {
				logger.error("{} Error: Cascading response metadata for ref ({}) {}: {}",
					origin, ref.getOrigin(), ref.getTitle(), ref.getUrl(), e);
			} finally {
				if (refRepository.clearCascade(ref.getUrl(), ref.getOrigin(), ref.getModified()) == 0) {
					logger.debug("{} Ref changed while cascading, retrying: {}", origin, ref.getUrl());
				}
			}
		}
	}

	/**
	 * Rebuild the metadata of the sources after the first {@link Meta#SYNC_SOURCES}
	 * and of the sources that are no longer cited by this Ref.
	 * Each source is updated in its own short transaction.
	 */
	public void cascadeRef(String origin, Ref ref) {
		var urls = new LinkedHashSet<String>();
		if (ref.getSources() != null) ref.getSources().stream()
			.skip(SYNC_SOURCES)
			.filter(s -> !s.equals(ref.getUrl()))
			.forEach(urls::add);
		refRepository.findRemovedSourceUrls(ref.getUrl(), origin).stream()
			.filter(s -> ref.getSources() == null || !ref.getSources().contains(s))
			.forEach(urls::add);
		var tx = new TransactionTemplate(transactionManager);
		for (var url : urls) {
			try {
				tx.executeWithoutResult(status -> refRepository.findAll(isUrl(url).and(isNotObsolete()).and(isUnderOrigin(origin)))
					.forEach(source -> meta.cascadeSource(origin, ref, source)));
			} catch (Exception e) {
				logger.error("{} Error cascading source metadata {} for ref ({}) {}",
					origin, url, ref.getOrigin(), ref.getUrl(), e);
			}
		}
	}
}
