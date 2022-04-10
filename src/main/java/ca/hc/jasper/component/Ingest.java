package ca.hc.jasper.component;

import static ca.hc.jasper.repository.spec.OriginSpec.isOrigin;
import static ca.hc.jasper.repository.spec.RefSpec.isUrls;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import ca.hc.jasper.domain.*;
import ca.hc.jasper.errors.*;
import ca.hc.jasper.repository.PluginRepository;
import ca.hc.jasper.repository.RefRepository;
import com.fasterxml.jackson.databind.*;
import com.jsontypedef.jtd.*;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Ingest {
	private static final Logger logger = LoggerFactory.getLogger(Ingest.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	Validator validator;

	@Autowired
	ObjectMapper objectMapper;

	void backfillMetadata() {
		List<Ref> all = refRepository.findAll(((root, query, cb) -> cb.isNull(root.get(Ref_.metadata))));
		for (var ref : all) {
			updateMetadata(ref, null);
			refRepository.save(ref);
		}
	}

	public void ingest(Ref ref) {
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		if (refRepository.existsByAlternateUrl(ref.getUrl())) throw new AlreadyExistsException();
		validate(ref, true);
		updateMetadata(ref, null);
		ref.setCreated(Instant.now());
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	public void update(Ref ref) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		validate(ref, false);
		updateMetadata(ref, existing);
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	public void delete(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return;
		updateMetadata(null, maybeExisting.get());
		refRepository.deleteByUrlAndOrigin(url, origin);
	}

	public void validate(Ref ref, boolean useDefaults) {
		validateTags(ref);
		validatePlugins(ref, useDefaults);
		validateSources(ref);
		validateResponses(ref);
	}

	public void validateTags(Ref ref) {
		if (ref.getTags() == null) return;
		if (!ref.getTags().stream().allMatch(new HashSet<>()::add)) {
			throw new DuplicateTagException();
		}
	}

	public void validatePlugins(Ref ref, boolean useDefaults) {
		if (ref.getTags() == null) return;
		if (ref.getPlugins() != null) {
			// Plugin fields must be tagged
			ref.getPlugins().fieldNames().forEachRemaining(field -> {
				if (field.equals("")) return;
				if (!ref.getTags().contains(field)) {
					throw new InvalidPluginException(field);
				}
			});
		}
		for (var tag : ref.getTags()) {
			validatePlugin(ref, tag, useDefaults);
		}
	}

	private <T extends JsonNode> T merge(T a, JsonNode b) {
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidPluginException("Merging", e);
		}
	}

	public void validatePlugin(Ref ref, String tag, boolean useDefaults) {
		var plugins = pluginRepository.findAllForTagAndOriginWithSchema(tag, ref.getOrigin());
		if (plugins.isEmpty()) {
			// If a tag has no plugin, or the plugin is schemaless, plugin data is not allowed
			if (ref.getPlugins() != null && ref.getPlugins().has(tag)) throw new InvalidPluginException(tag);
			return;
		}
		if (useDefaults && (ref.getPlugins() == null || !ref.getPlugins().has(tag))) {
			if (ref.getPlugins() == null) {
				ref.setPlugins(objectMapper.getNodeFactory().objectNode());
			}
			var mergedDefaults = plugins
				.stream()
				.map(Plugin::getDefaults)
				.filter(Objects::nonNull)
				.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
			ref.getPlugins().set(tag, mergedDefaults);
		}
		if (ref.getPlugins() == null) throw new InvalidPluginException(tag);
		if (!ref.getPlugins().has(tag)) throw new InvalidPluginException(tag);
		var mergedSchemas = plugins
			.stream()
			.map(Plugin::getSchema)
			.filter(Objects::nonNull)
			.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
		var pluginData = new JacksonAdapter(ref.getPlugins().get(tag));
		var schema = objectMapper.convertValue(mergedSchemas, Schema.class);
		try {
			var errors = validator.validate(schema, pluginData);
			for (var error : errors) {
				logger.debug("Error validating plugin {}: {}", tag, error);
			}
			if (errors.size() > 0) throw new InvalidPluginException(tag);
		} catch (MaxDepthExceededException e) {
			throw new InvalidPluginException(tag, e);
		}
	}

	public void validateSources(Ref ref) {
		if (ref.getSources() == null) return;
		for (var sourceUrl : ref.getSources()) {
			var sources = refRepository.findAllByUrlAndPublishedGreaterThanEqual(sourceUrl, ref.getPublished());
			for (var source : sources) {
				throw new PublishDateException(ref.getUrl(), source.getUrl());
			}
		}
	}

	public void validateResponses(Ref ref) {
		var responses = refRepository.findAllResponsesPublishedBefore(ref.getUrl(), ref.getPublished());
		for (var response : responses) {
			throw new PublishDateException(response, ref.getUrl());
		}
	}

	public void updateMetadata(Ref ref, Ref existing) {
		if (ref != null) {
			ref.setMetadata(Metadata
				.builder()
				.responses(refRepository.findAllResponsesByOriginWithoutTag(ref.getUrl(), ref.getOrigin(), "internal"))
				.internalResponses(refRepository.findAllResponsesByOriginWithTag(ref.getUrl(), ref.getOrigin(), "internal"))
				.plugins(pluginRepository
					.findAllByGenerateMetadata()
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
					.findAllByGenerateMetadata()
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
