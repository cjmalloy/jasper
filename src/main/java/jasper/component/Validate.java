package jasper.component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsontypedef.jtd.JacksonAdapter;
import com.jsontypedef.jtd.MaxDepthExceededException;
import com.jsontypedef.jtd.Schema;
import com.jsontypedef.jtd.Validator;
import io.micrometer.core.annotation.Timed;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.errors.DuplicateTagException;
import jasper.errors.InvalidPluginException;
import jasper.errors.InvalidTemplateException;
import jasper.errors.PublishDateException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
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
	TemplateRepository templateRepository;

	@Autowired
	Validator validator;

	@Autowired
	ObjectMapper objectMapper;

	@Timed("jasper.validate.ref")
	public void ref(Ref ref) {
		this.ref(ref, ref.getOrigin());
	}

	@Timed("jasper.validate.ref")
	public void ref(Ref ref, String validationOrigin) {
		tags(ref);
		plugins(ref, validationOrigin);
		sources(ref);
		responses(ref);
	}

	@Timed("jasper.validate.ext")
	public void ext(Ext ext) {
		this.ext(ext, ext.getOrigin());
	}

	@Timed("jasper.validate.ext")
	public void ext(Ext ext, String origin) {
		var templates = templateRepository.findAllForTagAndOriginWithSchema(ext.getTag(), origin);
		if (templates.isEmpty()) {
			// If an ext has no template, or the template is schemaless, no config is allowed
			if (ext.getConfig() != null) throw new InvalidTemplateException(ext.getTag());
			return;
		}
		if (ext.getConfig() == null) {
			var mergedDefaults = templates
				.stream()
				.map(Template::getDefaults)
				.filter(Objects::nonNull)
				.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
			ext.setConfig(mergedDefaults);
		}
		if (ext.getConfig() == null) throw new InvalidTemplateException(ext.getTag());
		var mergedSchemas = templates
			.stream()
			.map(Template::getSchema)
			.filter(Objects::nonNull)
			.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
		var tagConfig = new JacksonAdapter(ext.getConfig());
		var schema = objectMapper.convertValue(mergedSchemas, Schema.class);
		try {
			var errors = validator.validate(schema, tagConfig);
			for (var error : errors) {
				logger.debug("Error validating template {}: {}", ext.getTag(), error);
			}
			if (errors.size() > 0) throw new InvalidTemplateException(ext.getTag() + ": " + errors);
		} catch (MaxDepthExceededException e) {
			throw new InvalidTemplateException(ext.getTag(), e);
		}
	}

	private void tags(Ref ref) {
		if (ref.getTags() == null) return;
		if (!ref.getTags().stream().allMatch(new HashSet<>()::add)) {
			throw new DuplicateTagException();
		}
	}

	private void plugins(Ref ref, String origin) {
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
			plugin(ref, tag, origin);
		}
	}

	private <T extends JsonNode> T merge(T a, JsonNode b) {
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidPluginException("Merging", e);
		}
	}

	private void plugin(Ref ref, String tag, String origin) {
		var plugins = pluginRepository.findAllForTagAndOriginWithSchema(tag, origin);
		if (plugins.isEmpty()) {
			// If a tag has no plugin, or the plugin is schemaless, plugin data is not allowed
			if (ref.getPlugins() != null && ref.getPlugins().has(tag)) throw new InvalidPluginException(tag);
			return;
		}
		if (ref.getPlugins() == null || !ref.getPlugins().has(tag)) {
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
				ref.setPublished(source.getPublished().plusMillis(1));
			}
		}
	}

	private void responses(Ref ref) {
		var responses = refRepository.findAllResponsesPublishedBeforeThanEqual(ref.getUrl(), ref.getPublished());
		for (var response : responses) {
			throw new PublishDateException(response, ref.getUrl());
		}
	}
}
