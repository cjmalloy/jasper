package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.*;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static jasper.repository.spec.RefSpec.isUrls;

@Component
public class Meta {
	private static final Logger logger = LoggerFactory.getLogger(Meta.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Timed(value = "jasper.meta.update", histogram = true)
	public void update(Ref ref, Ref existing, List<String> metadataPlugins) {
		if (metadataPlugins == null) {
			var origin = ref != null ? ref.getOrigin() : existing.getOrigin();
			metadataPlugins = pluginRepository.findAllByGenerateMetadataByOrigin(origin);
		}
		if (ref != null) {
			// Creating or updating (not deleting)
			ref.setMetadata(Metadata
				.builder()
				.responses(refRepository.findAllResponsesWithoutTag(ref.getUrl(), "internal"))
				.internalResponses(refRepository.findAllResponsesWithTag(ref.getUrl(), "internal"))
				.plugins(metadataPlugins
					.stream()
					.map(tag -> new Pair<>(
						tag,
						refRepository.findAllResponsesWithTag(ref.getUrl(), tag)))
					.filter(p -> !p.getValue1().isEmpty())
					.collect(Collectors.toMap(Pair::getValue0, Pair::getValue1)))
				.build()
			);

			// Update sources
			if (ref.getTags() == null) ref.setTags(new ArrayList<>());
			var internal = ref.getTags().contains("internal");
			List<String> plugins = metadataPlugins.stream()
				.filter(tag -> ref.getTags().contains(tag))
				.toList();
			// TODO: Update sources without fetching
			List<Ref> sources = refRepository.findAll(isUrls(ref.getSources()));
			for (var source : sources) {
				var old = source.getMetadata();
				if (old == null) {
					logger.warn("Ref missing metadata: {}", ref.getUrl());
					old = Metadata
						.builder()
						.responses(new ArrayList<>())
						.internalResponses(new ArrayList<>())
						.plugins(new HashMap<>())
						.build();
				}
				if (internal) {
					old.addInternalResponse(ref.getUrl());
				} else {
					old.addResponse(ref.getUrl());
				}
				old.addPlugins(plugins, ref.getUrl());
				source.setMetadata(old);
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
				var old = source.getMetadata();
				if (old == null) {
					logger.warn("Ref missing metadata: {}", existing.getUrl());
					continue;
				}
				old.remove(existing.getUrl());
				source.setMetadata(old);
				refRepository.save(source);
			}
		}
	}
}
