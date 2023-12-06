package jasper.component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jsontypedef.jtd.JacksonAdapter;
import com.jsontypedef.jtd.MaxDepthExceededException;
import com.jsontypedef.jtd.Schema;
import com.jsontypedef.jtd.Validator;
import io.micrometer.core.annotation.Timed;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.errors.DuplicateTagException;
import jasper.errors.InvalidPluginException;
import jasper.errors.InvalidPluginUserUrlException;
import jasper.errors.InvalidTemplateException;
import jasper.errors.PublishDateException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
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
	Props props;

	@Autowired
	Auth auth;

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
	public void ref(Ref ref, boolean force) {
		try {
			if (!auth.hasRole(MOD)) ref.removeTags(Arrays.asList(props.getModSeals()));
			if (!auth.hasRole(EDITOR)) ref.removeTags(Arrays.asList(props.getEditorSeals()));
		} catch (ScopeNotActiveException e) {
			ref.removeTags(Arrays.asList(props.getModSeals()));
			ref.removeTags(Arrays.asList(props.getEditorSeals()));
		}
		ref(ref, ref.getOrigin(), force);
	}

	@Timed("jasper.validate.ref")
	public void ref(Ref ref, String validationOrigin, boolean force) {
		tags(ref);
		plugins(ref, validationOrigin, force);
		sources(ref);
		responses(ref);
	}

	@Timed("jasper.validate.ext")
	public void ext(Ext ext) {
		ext(ext,false);
	}

	@Timed("jasper.validate.ext")
	public void ext(Ext ext, boolean stripOnError) {
		ext(ext, ext.getOrigin(), stripOnError);
	}

	@Timed("jasper.validate.ext")
	public void ext(Ext ext, String origin, boolean stripOnError) {
		var templates = templateRepository.findAllForTagAndOriginWithSchema(ext.getTag(), origin);
		if (templates.isEmpty()) {
			// If an ext has no template, or the template is schemaless, no config is allowed
			if (ext.getConfig() != null && !ext.getConfig().isEmpty()) throw new InvalidTemplateException(ext.getTag());
			return;
		}
		var mergedDefaults = templates
			.stream()
			.map(Template::getDefaults)
			.filter(Objects::nonNull)
			.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
		if (ext.getConfig() == null) {
			ext.setConfig(mergedDefaults);
			stripOnError = false;
		}
		if (ext.getConfig() == null) throw new InvalidTemplateException(ext.getTag());
		var mergedSchemas = templates
			.stream()
			.map(Template::getSchema)
			.filter(Objects::nonNull)
			.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
		var schema = objectMapper.convertValue(mergedSchemas, Schema.class);
		if (stripOnError) {
			try {
				template(schema, ext.getTag(), mergedDefaults);
			} catch (Exception e) {
				// Defaults don't validate anyway,
				// so cancel stripping plugins to pass validation
				stripOnError = false;
			}
		}
		try {
			template(schema, ext.getTag(), ext.getConfig());
		} catch (Exception e) {
			if (!stripOnError) throw e;
			template(schema, ext.getTag(), mergedDefaults);
			ext.setConfig(mergedDefaults);
		}
	}

	public JsonNode templateDefaults(String qualifiedTag) {
		var qt = qt(qualifiedTag);
		var templates = templateRepository.findAllForTagAndOriginWithSchema(qt.tag, qt.origin);
		return templates
			.stream()
			.map(Template::getDefaults)
			.reduce(null, this::merge);
	}

	private void template(Schema schema, String tag, JsonNode template) {
		try {
			var errors = validator.validate(schema, new JacksonAdapter(template));
			for (var error : errors) {
				logger.debug("Error validating template {}: {}", tag, error);
			}
			if (errors.size() > 0) {
				throw new InvalidTemplateException(tag + ": " + errors);
			}
		} catch (MaxDepthExceededException e) {
			throw new InvalidTemplateException(tag, e);
		}
	}

	private void tags(Ref ref) {
		if (ref.getTags() == null) return;
		if (!ref.getTags().stream().allMatch(new HashSet<>()::add)) {
			throw new DuplicateTagException();
		}
	}

	private void plugins(Ref ref, String origin, boolean stripOnError) {
		if (ref.getPlugins() != null) {
			// Plugin fields must be tagged
			var strip = new ArrayList<String>();
			ref.getPlugins().fieldNames().forEachRemaining(field -> {
				if (field.equals("")) return;
				if (ref.getTags() == null || !ref.getTags().contains(field)) {
					if (!stripOnError) throw new InvalidPluginException(field);
					strip.add(field);
				}
			});
			strip.forEach(field -> ref.getPlugins().remove(field));
		}
		if (ref.getTags() == null) return;
		for (var tag : ref.getTags()) {
			plugin(ref, tag, origin, stripOnError);
		}
	}

	private <T extends JsonNode> T merge(T a, T b) {
		if (a == null) return b;
		if (b == null) return a;
		if (!a.isObject() || !b.isObject()) return null;
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidPluginException("Merging", e);
		}
	}

	private void plugin(Ref ref, String tag, String origin, boolean stripOnError) {
		var plugin = pluginRepository.findByTagAndOrigin(tag, origin);
		plugin.ifPresent(p -> {
			if (p.isUserUrl()) userUrl(ref, p);
		});
		if (plugin.isEmpty() || plugin.get().getSchema() == null) {
			// If a tag has no plugin, or the plugin is schemaless, plugin data is not allowed
			if (ref.getPlugins() != null && ref.getPlugins().has(tag)) {
				if (!stripOnError) throw new InvalidPluginException(tag);
				ref.getPlugins().remove(tag);
			}
			return;
		}
		var defaults = plugin
			.map(Plugin::getDefaults)
			.filter(n -> !n.isNull())
			.orElse(objectMapper.getNodeFactory().objectNode());
		if (ref.getPlugins() == null || !ref.getPlugins().has(tag)) {
			if (ref.getPlugins() == null) {
				ref.setPlugins(objectMapper.getNodeFactory().objectNode());
			}
			ref.getPlugins().set(tag, defaults);
			stripOnError = false;
		}
		var schema = objectMapper.convertValue(plugin.get().getSchema(), Schema.class);
		if (stripOnError) {
			try {
				plugin(schema, tag, defaults);
			} catch (Exception e) {
				// Defaults don't validate anyway,
				// so cancel stripping plugins to pass validation
				stripOnError = false;
			}
		}
		try {
			plugin(schema, tag, ref.getPlugins().get(tag));
		} catch (Exception e) {
			if (!stripOnError) throw e;
			plugin(schema, tag, defaults);
			ref.getPlugins().set(tag, defaults);
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
			var plugin = pluginRepository.findByTagAndOrigin(tag, ref.getOrigin());
			plugin.ifPresent(p -> {
				if (p.getDefaults() != null && !p.getDefaults().isEmpty()) result.set(tag, p.getDefaults());
			});
		}
		if (ref.getPlugins() != null) return merge(result, ref.getPlugins());
		return result;
	}

	private void plugin(Schema schema, String tag, JsonNode plugin) {
		try {
			var errors = validator.validate(schema, new JacksonAdapter(plugin));
			for (var error : errors) {
				logger.debug("Error validating plugin {}: {}", tag, error);
			}
			if (errors.size() > 0) {
				throw new InvalidPluginException(tag + ": " + errors);
			}
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
