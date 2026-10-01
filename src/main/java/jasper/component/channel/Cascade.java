package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.component.Meta;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.domain.RefId;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static jasper.component.Meta.SYNC_SOURCES;
import static jasper.repository.spec.OriginSpec.isUnderOrigin;
import static jasper.repository.spec.RefSpec.isNotObsolete;
import static jasper.repository.spec.RefSpec.isUrls;

/**
 * Updates the Metadata of the sources of Refs marked for cascade.
 * Only the first sources of a Ref are updated synchronously, the rest are updated here.
 */
@Profile("!no-cascade")
@Component
public class Cascade {
	private static final Logger logger = LoggerFactory.getLogger(Cascade.class);
	private static final int SOURCE_BATCH_SIZE = 1000;

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
		var readOnly = new TransactionTemplate(transactionManager);
		readOnly.setReadOnly(true);
		var keys = readOnly.execute(status -> {
			var result = new LinkedHashSet<RefId>();
			var sources = (ref.getSources() == null ? List.<String>of() : ref.getSources())
				.stream()
				.skip(SYNC_SOURCES)
				.filter(s -> !s.equals(ref.getUrl()))
				.distinct()
				.toList();
			for (var i = 0; i < sources.size(); i += SOURCE_BATCH_SIZE) {
				var batch = sources.subList(i, Math.min(i + SOURCE_BATCH_SIZE, sources.size()));
				for (var source : refRepository.findAll(isUrls(batch).and(isNotObsolete()).and(isUnderOrigin(origin)))) {
					result.add(new RefId(source.getUrl(), source.getOrigin()));
				}
			}
			try (var stream = refRepository.findRemovedSources(ref.getUrl(), origin)) {
				stream
					.filter(s -> ref.getSources() == null || !ref.getSources().contains(s.getUrl()))
					.forEach(source -> result.add(new RefId(source.getUrl(), source.getOrigin())));
			}
			return result;
		});
		var tx = new TransactionTemplate(transactionManager);
		for (var key : keys) {
			try {
				tx.executeWithoutResult(status -> refRepository.findOneByUrlAndOrigin(key.getUrl(), key.getOrigin())
					.ifPresent(source -> meta.cascadeSource(origin, ref, source)));
			} catch (Exception e) {
				logger.error("{} Error cascading source metadata ({}) {} for ref ({}) {}",
					origin, key.getOrigin(), key.getUrl(), ref.getOrigin(), ref.getUrl(), e);
			}
		}
	}
}
