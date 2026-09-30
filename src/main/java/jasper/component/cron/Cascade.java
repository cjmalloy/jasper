package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.Messages;
import jasper.component.Meta;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import jasper.repository.spec.OriginSpec;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static jasper.component.Meta.SYNC_SOURCES;
import static jasper.repository.spec.OriginSpec.isUnderOrigin;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasResponse;
import static jasper.repository.spec.RefSpec.isUrls;
import static java.util.Objects.requireNonNullElse;

/**
 * Updates the Metadata of the sources of Refs marked for cascade.
 * Only the first sources of a Ref are updated synchronously, the rest are updated here.
 */
@Profile("!no-cascade")
@Component
public class Cascade {
	private static final Logger logger = LoggerFactory.getLogger(Cascade.class);
	private static final int CASCADE_BATCH = 1000;

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
				if (!cascadeRef(origin, ref)) {
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

	/**
	 * Regenerate metadata for the sources of a Ref marked for cascade that were not
	 * updated synchronously, as well as any sources it no longer cites.
	 *
	 * @return false if a source was modified concurrently and the cascade should be retried
	 */
	@Timed(value = "jasper.meta", histogram = true)
	boolean cascadeRef(String rootOrigin, Ref ref) {
		var cited = ref.getSources() == null ? List.<String>of() : ref.getSources();
		var sources = otherSources(ref.getUrl(), cited);
		var cascade = new LinkedHashSet<Ref>();
		for (var i = SYNC_SOURCES; i < sources.size(); i += CASCADE_BATCH) {
			cascade.addAll(refRepository.findAll(
				isUrls(sources.subList(i, Math.min(i + CASCADE_BATCH, sources.size()))).and(isUnderOrigin(rootOrigin))));
		}
		cascade.addAll(refRepository.findAll(OriginSpec.<Ref>isUnderOrigin(rootOrigin)
				.and(hasResponse(ref.getUrl()).or(hasInternalResponse(ref.getUrl()))))
			.stream()
			.filter(s -> !cited.contains(s.getUrl()))
			.filter(s -> s.getAlternateUrls() == null || s.getAlternateUrls().stream().noneMatch(cited::contains))
			.toList());
		var result = true;
		for (var source : cascade) {
			if (source.getUrl().equals(ref.getUrl())) continue;
			var existing = source.getMetadata();
			meta.ref(rootOrigin, source);
			if (existing != null) {
				source.getMetadata().setObsolete(existing.isObsolete());
				source.getMetadata().setRegen(existing.isRegen());
				source.getMetadata().setCascade(existing.isCascade());
			}
			var updated = refRepository.updateMetadataIfUnmodified(
				source.getUrl(),
				source.getOrigin(),
				source.getModified(),
				existing == null ? "" : requireNonNullElse(existing.getModified(), ""),
				source.getMetadata());
			if (updated == 0) {
				result = false;
				continue;
			}
			messages.updateMetadata(source);
		}
		return result;
	}

	private static List<String> otherSources(String url, List<String> sources) {
		if (sources == null) return List.of();
		return sources.stream().filter(s -> !url.equals(s)).distinct().toList();
	}
}
