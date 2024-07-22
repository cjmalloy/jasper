package jasper.component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jsontypedef.jtd.JacksonAdapter;
import com.jsontypedef.jtd.MaxDepthExceededException;
import com.jsontypedef.jtd.Schema;
import com.jsontypedef.jtd.Validator;
import io.micrometer.core.annotation.Timed;
import jasper.config.Config.SecurityConfig;
import jasper.config.Config.ServerConfig;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.errors.DuplicateTagException;
import jasper.errors.InvalidPluginException;
import jasper.errors.InvalidPluginUserUrlException;
import jasper.errors.InvalidTemplateException;
import jasper.errors.PublishDateException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.TemplateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

import static jasper.domain.proj.Tag.urlForUser;
import static jasper.repository.spec.QualifiedTag.qt;
import static jasper.security.AuthoritiesConstants.EDITOR;
import static jasper.security.AuthoritiesConstants.MOD;

@Component
public class Validate {
	private static final Logger logger = LoggerFactory.getLogger(Validate.class);

	@Autowired
	Auth auth;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Validator validator;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ConfigCache configs;

	@Timed("jasper.validate")
	public void ref(String origin, Ref ref, boolean force) {
		var root = configs.root();
		try {
			if (!auth.hasRole(MOD)) ref.removeTags(root.getModSeals());
			if (!auth.hasRole(EDITOR)) ref.removeTags(root.getEditorSeals());
		} catch (ScopeNotActiveException e) {
			ref.removeTags(root.getModSeals());
			ref.removeTags(root.getEditorSeals());
		}
		ref(origin, ref, ref.getOrigin(), force);
	}

	@Timed("jasper.validate")
	public void ref(String origin, Ref ref, String pluginOrigin, boolean force) {
		tags(origin, ref);
		plugins(origin, ref, pluginOrigin, force);
		sources(origin, ref, true);
		responses(origin, ref, true);
		sources(origin, ref, false);
		responses(origin, ref, false);
	}

	@Timed("jasper.validate")
	public void ext(String origin, Ext ext) {
		ext(origin, ext,false);
	}

	@Timed("jasper.validate")
	public void ext(String origin, Ext ext, boolean stripOnError) {
		ext(origin, ext, ext.getOrigin(), stripOnError);
	}

	@Timed("jasper.validate")
	public void ext(String origin, Ext ext, String validationOrigin, boolean stripOnError) {
		var templates = configs.getSchemas(ext.getTag(), validationOrigin);
		if (templates.isEmpty()) {
			// If an ext has no template, or the template is schemaless, no config is allowed
			if (ext.getConfig() != null && !ext.getConfig().isEmpty()) throw new InvalidTemplateException(ext.getTag());
			return;
		}
		var mergedDefaults = templates
			.stream()
			.map(TemplateDto::getDefaults)
			.filter(Objects::nonNull)
			.reduce(null, this::merge);
		if (ext.getConfig() == null) {
			ext.setConfig(mergedDefaults);
			stripOnError = true;
		}
		var mergedSchemas = templates
			.stream()
			.map(TemplateDto::getSchema)
			.filter(Objects::nonNull)
			.reduce(null, this::merge);
		var schema = objectMapper.convertValue(mergedSchemas, Schema.class);
		if (stripOnError) {
			try {
				template(origin, schema, ext.getTag(), mergedDefaults);
			} catch (Exception e) {
				logger.error("{} Defaults for {} Template do not pass validation", origin, ext.getTag());
				// Defaults don't validate anyway,
				// so cancel stripping plugins to pass validation
				stripOnError = false;
			}
		}
		try {
			template(origin, schema, ext.getTag(), ext.getConfig());
		} catch (Exception e) {
			if (!stripOnError) throw e;
			template(origin, schema, ext.getTag(), mergedDefaults);
			ext.setConfig(mergedDefaults);
		}
	}

	@Timed("jasper.validate")
	public void plugin(String origin, Plugin plugin) {

	}

	@Timed("jasper.validate")
	public void template(String origin, Template template) {
		try {
			switch (template.getTag()) {
				case "_config/server":
					objectMapper.convertValue(template.getConfig(), ServerConfig.class);
					break;
				case "_config/security":
					objectMapper.convertValue(template.getConfig(), SecurityConfig.class);
					break;
			}
		} catch (Exception e) {
			throw new InvalidTemplateException(template.getTag());
		}
	}

	public JsonNode templateDefaults(String qualifiedTag) {
		var qt = qt(qualifiedTag);
		var templates = configs.getSchemas(qt.tag, qt.origin);
		return templates
			.stream()
			.map(TemplateDto::getDefaults)
			.reduce(null, this::merge);
	}

	private void template(String origin, Schema schema, String tag, JsonNode template) {
		if (template == null || template.isNull()) {
			// Allow null to stand in for empty config
			if (schema.getOptionalProperties() != null) {
				template = objectMapper.createObjectNode();
			} else if (template == null) {
				template = NullNode.getInstance();
			}
		}
		try {
			var errors = validator.validate(schema, new JacksonAdapter(template));
			for (var error : errors) {
				logger.debug("{} Error validating template {}: {}", origin, tag, error);
			}
			if (!errors.isEmpty()) {
				throw new InvalidTemplateException(tag + ": " + errors);
			}
		} catch (MaxDepthExceededException e) {
			throw new InvalidTemplateException(tag, e);
		}
	}

	private void tags(String origin, Ref ref) {
		if (ref.getTags() == null) return;
		if (!ref.getTags().stream().allMatch(new HashSet<>()::add)) {
			throw new DuplicateTagException();
		}
	}

	private void plugins(String origin, Ref ref, String pluginOrigin, boolean stripOnError) {
		if (ref.getPlugins() != null) {
			// Plugin fields must be tagged
			var strip = new ArrayList<String>();
			ref.getPlugins().fieldNames().forEachRemaining(field -> {
				if (ref.getTags() == null || !ref.getTags().contains(field)) {
					logger.debug("{} Plugin missing tag: {}", origin, field);
					if (!stripOnError) throw new InvalidPluginException(field);
					strip.add(field);
				}
			});
			strip.forEach(field -> ref.getPlugins().remove(field));
		}
		if (ref.getTags() == null) return;
		for (var tag : ref.getTags()) {
			plugin(origin, ref, tag, pluginOrigin, stripOnError);
		}
	}

	private <T extends JsonNode> T merge(T a, T b) {
		if (a == null) return b.deepCopy();
		if (b == null) return a.deepCopy();
		if (!a.isObject() || !b.isObject()) return b.deepCopy();
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidPluginException("Merging", e);
		}
	}

	private void plugin(String origin, Ref ref, String tag, String pluginOrigin, boolean stripOnError) {
		var plugin = configs.getPlugin(tag, pluginOrigin);
		plugin.ifPresent(p -> {
			if (p.isUserUrl()) userUrl(ref, p);
		});
		if (plugin.isEmpty() || plugin.get().getSchema() == null) {
			// If a tag has no plugin, or the plugin is schemaless, plugin data is not allowed
			if (ref.hasPlugin(tag)) {
				logger.debug("{} Plugin data not allowed: {}", origin, tag);
				if (!stripOnError) throw new InvalidPluginException(tag);
				ref.getPlugins().remove(tag);
			}
			return;
		}
		var defaults = plugin.map(Plugin::getDefaults).orElse(null);
		if (!ref.hasPlugin(tag)) {
			ref.setPlugin(tag, defaults);
			stripOnError = true;
		}
		var schema = objectMapper.convertValue(plugin.get().getSchema(), Schema.class);
		if (stripOnError) {
			try {
				plugin(ref.getOrigin(), schema, tag, defaults);
			} catch (Exception e) {
				logger.error("{} Defaults for {} Plugin do not pass validation", origin, tag);
				// Defaults don't validate anyway,
				// so cancel stripping plugins to pass validation
				stripOnError = false;
			}
		}
		try {
			plugin(ref.getOrigin(), schema, tag, ref.getPlugin(tag));
		} catch (Exception e) {
			if (!stripOnError) throw e;
			ref.setPlugin(tag, defaults);
		}
	}

	private void userUrl(Ref ref, Plugin plugin) {
		if (ref.getSources() == null || ref.getSources().size() != 1) {
			throw new InvalidPluginUserUrlException(plugin.getTag());
		}
		var userTag = ref.getTags().stream().filter(t -> t.startsWith("+user") || t.startsWith("_user")).findFirst();
		if (userTag.isEmpty() || !ref.getUrl().startsWith(urlForUser(ref.getSources().get(0), userTag.get()))) {
			throw new InvalidPluginUserUrlException(plugin.getTag());
		}
	}

	public ObjectNode pluginDefaults(Ref ref) {
		var result = objectMapper.getNodeFactory().objectNode();
		if (ref.getTags() == null) return result;
		for (var tag : ref.getTags()) {
			var plugin = configs.getPlugin(tag, ref.getOrigin());
			plugin.ifPresent(p -> {
				if (p.getDefaults() != null && !p.getDefaults().isEmpty()) result.set(tag, p.getDefaults());
			});
		}
		if (ref.getPlugins() != null) return merge(result, ref.getPlugins());
		return result;
	}

	private void plugin(String origin, Schema schema, String tag, JsonNode plugin) {
		if (plugin == null || plugin.isNull()) {
			// Allow null to stand in for empty objects or arrays
			if (schema.getOptionalProperties() != null) {
				plugin = objectMapper.createObjectNode();
			} else if (schema.getElements() != null) {
				plugin = objectMapper.createArrayNode();
			} else if (plugin == null) {
				plugin = NullNode.getInstance();
			}
		}
		try {
			var errors = validator.validate(schema, new JacksonAdapter(plugin));
			for (var error : errors) {
				logger.debug("{} Error validating plugin {}: {}", origin, tag, error);
			}
			if (!errors.isEmpty()) {
				throw new InvalidPluginException(tag + ": " + errors);
			}
		} catch (MaxDepthExceededException e) {
			throw new InvalidPluginException(tag, e);
		}
	}

	private void sources(String origin, Ref ref, boolean fix) {
		if (ref.getSources() == null) return;
		for (var sourceUrl : ref.getSources()) {
			var sources = refRepository.findAllPublishedByUrlAndPublishedGreaterThanEqual(sourceUrl, ref.getOrigin(), ref.getPublished());
			for (var source : sources) {
				if (!fix) throw new PublishDateException(source.getUrl(), ref.getUrl());
				if (source.getPublished().isAfter(ref.getPublished())) {
					ref.setPublished(source.getPublished().plusMillis(1));
				}
			}
		}
	}

	private void responses(String origin, Ref ref, boolean fix) {
		var responses = refRepository.findAllResponsesPublishedBeforeThanEqual(ref.getUrl(), ref.getOrigin(), ref.getPublished());
		for (var response : responses) {
			if (!fix) throw new PublishDateException(response.getUrl(), ref.getUrl());
			if (response.getPublished().isBefore(ref.getPublished())) {
				ref.setPublished(response.getPublished().minusMillis(1));
			}
		}
	}
}
