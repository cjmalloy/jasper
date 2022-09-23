package jasper.component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsontypedef.jtd.JacksonAdapter;
import com.jsontypedef.jtd.MaxDepthExceededException;
import com.jsontypedef.jtd.Schema;
import com.jsontypedef.jtd.Validator;
import io.micrometer.core.annotation.Counted;
import jasper.config.ApplicationProperties;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.DuplicateTagException;
import jasper.errors.InvalidPluginException;
import jasper.errors.NotFoundException;
import jasper.errors.PublishDateException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Component
public class Ingest {
	private static final Logger logger = LoggerFactory.getLogger(Ingest.class);

	@Autowired
	ApplicationProperties applicationProperties;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	Validator validator;

	@Autowired
	Meta meta;

	@Autowired
	ObjectMapper objectMapper;

	void backfillMetadata() {
		List<Ref> all = refRepository.findAll();
		for (var ref : all) {
			meta.update(ref, null);
			refRepository.save(ref);
		}
	}

	@Counted("jasper.ref.create")
	public void ingest(Ref ref) {
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		if (refRepository.existsByAlternateUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		ref.addHierarchicalTags();
		validate(ref, true);
		meta.update(ref, null);
		ref.setCreated(Instant.now());
		ensureUniqueModified(ref);
	}

	@Counted("jasper.ref.update")
	public void update(Ref ref) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref");
		var existing = maybeExisting.get();
		ref.addHierarchicalTags();
		validate(ref, false);
		meta.update(ref, existing);
		ensureUniqueModified(ref);
	}

	private void ensureUniqueModified(Ref ref) {
		var count = 0;
		while (true) {
			try {
				count++;
				ref.setModified(Instant.now());
				refRepository.save(ref);
				break;
			} catch (DataIntegrityViolationException e) {
				if (count > applicationProperties.getIngestMaxRetry()) {
					throw new DuplicateModifiedDateException();
				}
			}
		}
	}

	@Counted("jasper.ref.delete")
	public void delete(String url, String origin) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) return;
		meta.update(null, maybeExisting.get());
		refRepository.deleteByUrlAndOrigin(url, origin);
	}

	@Counted("jasper.ref.validate")
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
		var mergedSchemas = plugins
			.stream()
			.map(Plugin::getSchema)
			.filter(Objects::nonNull)
			.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
		var pluginData = new JacksonAdapter((ref.getPlugins() != null && ref.getPlugins().has(tag))
			? ref.getPlugins().get(tag)
			: objectMapper.getNodeFactory().objectNode());
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

}
