package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static jasper.repository.spec.RefSpec.isUrls;

@Component
public class Meta {
	private static final Logger logger = LoggerFactory.getLogger(Meta.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Timed(value = "jasper.meta.update", histogram = true)
	public void update(Ref ref, Ref existing, List<String> metadataPlugins) {
		if (metadataPlugins == null) {
			var origin = ref != null ? ref.getOrigin() : existing.getOrigin();
			// TODO: Need to consider the origin of the source when deciding to generate metadata
			metadataPlugins = pluginRepository.findAllByGenerateMetadataByOrigin(origin);
		}
		if (ref != null) {
			// Creating or updating (not deleting)
			ref.setMetadata(Metadata
				.builder()
				// TODO: multi-tenant filter instead of all origins
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

			// Set Obsolete
			ref.getMetadata().setObsolete(refRepository.existsByUrlAndModifiedGreaterThan(ref.getUrl(), ref.getOrigin(), ref.getModified()));
			refRepository.setObsolete(ref.getUrl(), ref.getOrigin(), ref.getModified());

			// Update sources
			var internal = ref.getTags() != null && ref.getTags().contains("internal");
			List<String> plugins = metadataPlugins.stream()
				.filter(tag -> ref.getTags() != null && ref.getTags().contains(tag))
				.toList();
			// TODO: Update sources without fetching
			List<Ref> sources = refRepository.findAll(isUrls(ref.getSources()));
			for (var source : sources) {
				var metadata = source.getMetadata();
				if (metadata == null) {
					logger.warn("Ref missing metadata: {}", ref.getUrl());
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
				refRepository.save(source);
			}
		}

		if (existing != null && existing.getSources() != null) {

			// Updating or deleting (not new)
			var removedSources = ref == null
				? existing.getSources()
				: existing.getSources()
				.stream()
				.filter(s -> ref.getSources() == null || !ref.getSources().contains(s))
				.toList();
			// TODO: Update sources without fetching
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
				refRepository.save(source);
			}
		}
	}
}
