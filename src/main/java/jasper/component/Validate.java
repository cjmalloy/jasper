package jasper.component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsontypedef.jtd.JacksonAdapter;
import com.jsontypedef.jtd.MaxDepthExceededException;
import com.jsontypedef.jtd.Schema;
import com.jsontypedef.jtd.Validator;
import io.micrometer.core.annotation.Timed;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.errors.DuplicateTagException;
import jasper.errors.InvalidPluginException;
import jasper.errors.PublishDateException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;

@Component
public class Validate {
	private static final Logger logger = LoggerFactory.getLogger(Validate.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	Validator validator;

	@Autowired
	ObjectMapper objectMapper;

	@Timed("jasper.validate.ref")
	public void ref(Ref ref, boolean useDefaults) {
		tags(ref);
		plugins(ref, useDefaults);
		sources(ref);
		responses(ref);
	}

	private void tags(Ref ref) {
		if (ref.getTags() == null) return;
		if (!ref.getTags().stream().allMatch(new HashSet<>()::add)) {
			throw new DuplicateTagException();
		}
	}

	private void plugins(Ref ref, boolean useDefaults) {
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
			plugin(ref, tag, useDefaults);
		}
	}

	private <T extends JsonNode> T merge(T a, JsonNode b) {
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidPluginException("Merging", e);
		}
	}

	private void plugin(Ref ref, String tag, boolean useDefaults) {
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
			if (errors.size() > 0) throw new InvalidPluginException(tag + ": " + errors);
		} catch (MaxDepthExceededException e) {
			throw new InvalidPluginException(tag, e);
		}
	}

	private void sources(Ref ref) {
		if (ref.getSources() == null) return;
		for (var sourceUrl : ref.getSources()) {
			var sources = refRepository.findAllByUrlAndPublishedGreaterThanEqual(sourceUrl, ref.getPublished());
			for (var source : sources) {
				throw new PublishDateException(ref.getUrl(), source.getUrl());
			}
		}
	}

	private void responses(Ref ref) {
		var responses = refRepository.findAllResponsesPublishedBefore(ref.getUrl(), ref.getPublished());
		for (var response : responses) {
			throw new PublishDateException(response, ref.getUrl());
		}
	}
}
