package jasper.component;

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
import java.util.Map;
import java.util.stream.Collectors;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrls;

@Component
public class Meta {
	private static final Logger logger = LoggerFactory.getLogger(Meta.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	public void update(Ref ref, Ref existing) {
		// TODO: make async
		if (ref != null) {
			// Creating or updating (not deleting)
			ref.setMetadata(Metadata
				.builder()
				.responses(refRepository.findAllResponsesByOriginWithoutTag(ref.getUrl(), ref.getOrigin(), "internal"))
				.internalResponses(refRepository.findAllResponsesByOriginWithTag(ref.getUrl(), ref.getOrigin(), "internal"))
				.plugins(pluginRepository
					.findAllByGenerateMetadataByOrigin(ref.getOrigin())
					.stream()
					.map(tag -> new Pair<>(
						tag,
						refRepository.findAllResponsesByOriginWithTag(ref.getUrl(), ref.getOrigin(), tag)))
					.collect(Collectors.toMap(Pair::getValue0, Pair::getValue1)))
				.build()
			);

			if (ref.getTags() != null) {
				// Update sources
				var internal = ref.getTags().contains("internal");
				Map<String, String> plugins = pluginRepository
					.findAllByGenerateMetadataByOrigin(ref.getOrigin())
					.stream()
					.filter(tag -> ref.getTags() != null && ref.getTags().contains(tag))
					.collect(Collectors.toMap(tag -> tag, tag -> ref.getUrl()));
				List<Ref> sources = refRepository.findAll(
					isUrls(ref.getSources())
						.and(isOrigin(ref.getOrigin())));
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
					old.addPlugins(plugins);
					source.setMetadata(old);
					refRepository.save(source);
				}
			}
		}

		if (existing != null && existing.getSources() != null) {
			// Updating or deleting (now new)
			var removedSources = ref == null
				? existing.getSources()
				: existing.getSources()
				.stream()
				.filter(s -> ref.getSources() == null || !ref.getSources().contains(s))
				.toList();
			List<Ref> removed = refRepository.findAll(
				isUrls(removedSources)
					.and(isOrigin(existing.getOrigin())));
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
