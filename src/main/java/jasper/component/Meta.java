package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.repository.RefRepository;
import org.javatuples.Pair;
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

	@Timed(value = "jasper.meta", histogram = true)
	public void ref(Ref ref, String metadataPluginOrigin) {
		if (ref == null) return;
		// Creating or updating (not deleting)
		var metadataPlugins = configs.getMetadataPlugins(metadataPluginOrigin);
		ref.setMetadata(Metadata
			.builder()
			.responses(refRepository.findAllResponsesWithoutTag(ref.getUrl(), ref.getOrigin(), "internal"))
			.internalResponses(refRepository.findAllResponsesWithTag(ref.getUrl(), ref.getOrigin(), "internal"))
			.plugins(metadataPlugins
				.stream()
				.map(tag -> new Pair<>(
					tag,
					refRepository.findAllResponsesWithTag(ref.getUrl(), ref.getOrigin(), tag)))
				.filter(p -> !p.getValue1().isEmpty())
				.collect(Collectors.toMap(Pair::getValue0, Pair::getValue1)))
			.build()
		);

		// Set Not Obsolete
		ref.getMetadata().setObsolete(false);
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void sources(Ref ref, Ref existing, String metadataPluginOrigin) {
		var metadataPlugins = configs.getMetadataPlugins(metadataPluginOrigin);
		if (ref != null) {
			// Creating or updating (not deleting)
			refRepository.setObsolete(ref.getUrl(), ref.getOrigin(), ref.getModified());

			// Update sources
			var internal = ref.getTags() != null && ref.getTags().contains("internal");
			List<String> plugins = metadataPlugins.stream()
				.filter(tag -> ref.getTags() != null && ref.getTags().contains(tag))
				.toList();
			// TODO: Need to consider the origin of the source when deciding to generate metadata
			List<Ref> sources = refRepository.findAll(isUrls(ref.getSources()));
			for (var source : sources) {
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
				} catch (DataAccessException e) {
					logger.error("Error updating source metadata for {} {}", ref.getOrigin(), ref.getUrl(), e);
				}
			}
		} else {
			// Deleting
			var maybeLatest = refRepository.findAll(isUrl(existing.getUrl()), PageRequest.of(0, 1, by(desc(Ref_.MODIFIED))));
			if (!maybeLatest.isEmpty()) {
				var latest = maybeLatest.getContent().get(0);
				if (latest.getMetadata() != null) {
					latest.getMetadata().setObsolete(false);
					refRepository.save(latest);
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
			// TODO: Need to consider the origin of the source when deciding to generate metadata
			List<Ref> removed = refRepository.findAll(isUrls(removedSources));
			for (var source : removed) {
				var metadata = source.getMetadata();
				if (metadata == null) {
					logger.warn("Ref missing metadata: {}", existing.getUrl());
					continue;
				}
				// TODO: Only do this part async, as we don't know if there is a duplicate response in another origin
				metadata.remove(existing.getUrl());
				source.setMetadata(metadata);
				try {
					refRepository.save(source);
				} catch (DataAccessException e) {
					logger.error("Error updating source metadata for {} {}", existing.getOrigin(), existing.getUrl(), e);
				}
			}
		}
	}
}
