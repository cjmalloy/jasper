package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.repository.RefRepository;
import jasper.repository.spec.OriginSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static jasper.repository.spec.OriginSpec.isUnderOrigin;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasResponse;
import static jasper.repository.spec.RefSpec.isUrl;
import static jasper.repository.spec.RefSpec.isUrls;
import static org.springframework.data.domain.Sort.Order.desc;
import static org.springframework.data.domain.Sort.by;

@Component
public class Meta {
	private static final Logger logger = LoggerFactory.getLogger(Meta.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	ConfigCache configs;

	@Autowired
	Messages messages;

	private record PluginResponses(String tag, List<String> responses) { }

	@Timed(value = "jasper.meta", histogram = true)
	public void ref(Ref ref, String rootOrigin) {
		if (ref == null) return;
		// Creating or updating (not deleting)
		var metadataPlugins = configs.getMetadataPlugins(rootOrigin);
		ref.setMetadata(Metadata
			.builder()
			.responses(refRepository.findAllResponsesWithoutTag(ref.getUrl(), ref.getOrigin(), "internal"))
			.internalResponses(refRepository.findAllResponsesWithTag(ref.getUrl(), ref.getOrigin(), "internal"))
			.plugins(metadataPlugins
				.stream()
				.map(tag -> new PluginResponses(
					tag,
					refRepository.findAllResponsesWithTag(ref.getUrl(), ref.getOrigin(), tag)))
				.filter(p -> !p.responses.isEmpty())
				.collect(Collectors.toMap(PluginResponses::tag, PluginResponses::responses)))
			.build()
		);

		// Set Not Obsolete
		ref.getMetadata().setObsolete(false);
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void regen(Ref ref, String rootOrigin) {
		ref(ref, rootOrigin);
		ref.getMetadata().setObsolete(refRepository.newerExists(ref.getUrl(), rootOrigin, ref.getModified()));
		if (ref.getMetadata().isObsolete()) return;
		refRepository.setObsolete(ref.getUrl(), rootOrigin, ref.getModified());
		var cleanupSources = refRepository.findAll(OriginSpec.<Ref>isUnderOrigin(rootOrigin)
			.and(hasResponse(ref.getUrl()).or(hasInternalResponse(ref.getUrl()))));
		for (var source : cleanupSources) {
			if (ref.getSources() != null && ref.getSources().contains(source.getUrl())) {
				ref(source, rootOrigin);
			} else {
				removeSource(source, ref.getUrl(), rootOrigin);
			}
		}
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void sources(Ref ref, Ref existing, String rootOrigin) {
		var metadataPlugins = configs.getMetadataPlugins(rootOrigin);
		if (ref != null) {
			// Creating or updating (not deleting)
			refRepository.setObsolete(ref.getUrl(), rootOrigin, ref.getModified());

			// Update sources
			var internal = ref.getTags() != null && ref.getTags().contains("internal");
			List<String> plugins = metadataPlugins.stream()
				.filter(tag -> ref.getTags() != null && ref.getTags().contains(tag))
				.toList();
			List<Ref> sources = refRepository.findAll(isUrls(ref.getSources()).and(isUnderOrigin(rootOrigin)));
			for (var source : sources) {
				if (source.getUrl().equals(ref.getUrl())) continue;
				var metadata = source.getMetadata();
				if (metadata == null) {
					logger.debug("Ref missing metadata: {}", ref.getUrl());
					metadata = Metadata
						.builder()
						.responses(new ArrayList<>())
						.internalResponses(new ArrayList<>())
						.plugins(new HashMap<>())
						.build();
				}
				metadata.remove(ref.getUrl());
				if (internal) {
					metadata.addInternalResponse(ref.getUrl());
				} else {
					metadata.addResponse(ref.getUrl());
				}
				metadata.addPlugins(plugins, ref.getUrl());
				source.setMetadata(metadata);
				try {
					refRepository.save(source);
					messages.updateMetadata(source);
				} catch (DataAccessException e) {
					logger.error("Error updating source metadata for {} {}", ref.getOrigin(), ref.getUrl(), e);
				}
			}
		} else {
			// Deleting
			var maybeLatest = refRepository.findAll(isUrl(existing.getUrl()).and(isUnderOrigin(rootOrigin)), PageRequest.of(0, 1, by(desc(Ref_.MODIFIED))));
			if (!maybeLatest.isEmpty()) {
				var latest = maybeLatest.getContent().get(0);
				if (latest.getMetadata() != null) {
					latest.getMetadata().setObsolete(false);
					refRepository.save(latest);
					messages.updateMetadata(latest);
				}
			}
		}

		if (existing != null && existing.getSources() != null) {
			// Updating or deleting (not new)
			var removedSources = ref == null
				? existing.getSources()
				: existing.getSources().stream()
					.filter(s -> ref.getSources() == null || !ref.getSources().contains(s))
					.toList();
			List<Ref> removed = refRepository.findAll(isUrls(removedSources).and(isUnderOrigin(rootOrigin)));
			for (var source : removed) {
				if (source.getUrl().equals(existing.getUrl())) continue;
				messages.updateMetadata(source);
				removeSource(source, existing.getUrl(), rootOrigin);
			}
		}
	}

	private void removeSource(Ref source, String url, String rootOrigin) {
		var metadata = source.getMetadata();
		if (metadata == null) return;
		metadata.remove(url);
		source.setMetadata(metadata);
		try {
			refRepository.save(source);
		} catch (DataAccessException e) {
			logger.error("{} Error updating source metadata for {} {}",
				rootOrigin, source.getOrigin(), source.getUrl(), e);
		}
	}
}
