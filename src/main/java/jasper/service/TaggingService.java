package jasper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.Patch;
import io.micrometer.core.annotation.Timed;
import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.Tagger;
import jasper.component.Validate;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.errors.DuplicateTagException;
import jasper.errors.InvalidPatchException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static jasper.component.Meta.expandTags;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class TaggingService {
	private static final Logger logger = LoggerFactory.getLogger(TaggingService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	ConfigCache configs;

	@Autowired
	Ingest ingest;

	@Autowired
	Tagger tagger;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@Autowired
	Validate validate;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canTag(#tag, #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public Instant create(String tag, String url, String origin) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (ref.hasTag(tag)) throw new DuplicateTagException(tag);
		ref.addTag(tag);
		ingest.update(auth.getOrigin(), ref);
		return ref.getModified();
	}

	@PreAuthorize("@auth.canUntag(#tag, #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public Instant delete(String tag, String url, String origin) {
		if (tag.equals("locked")) {
			throw new AccessDeniedException("Cannot unlock Ref");
		}
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (!ref.hasTag(tag)) return ref.getModified();
		if (ref.hasTag("locked") && ref.hasPlugin(tag)) {
			throw new AccessDeniedException("Cannot untag locked Ref with plugin data");
		}
		ref.removeTag(tag);
		ingest.update(auth.getOrigin(), ref);
		return ref.getModified();
	}

	@PreAuthorize("@auth.canPatchTags(#tags, #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public Instant tag(List<String> tags, String url, String origin) {
		if (tags.contains("-locked")) {
			throw new AccessDeniedException("Cannot unlock Ref");
		}
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (ref.hasTag("locked")) {
			for (var t : tags) {
				if (t.startsWith("-") && ref.hasPlugin(t.substring(1))) {
					throw new AccessDeniedException("Cannot untag locked Ref with plugin data");
				}
			}
		}
		ref.addTags(tags);
		ingest.update(auth.getOrigin(), ref);
		return ref.getModified();
	}

	@PreAuthorize("@auth.isLoggedIn() and @auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public RefDto getResponse(String url) {
		var ref = tagger.getResponseRef(auth.getUserTag().tag, auth.getOrigin(), url);
		return mapper.domainToDto(ref);
	}

	@PreAuthorize("@auth.isLoggedIn() and @auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void createResponse(String tag, String url) {
		var ref = tagger.getResponseRef(auth.getUserTag().tag, auth.getOrigin(), url);
		if (isNotBlank(tag) && !ref.hasTag(tag)) {
			ref.addTag(tag);
			try {
				ingest.updateResponse(auth.getOrigin(), ref);
			} catch (ModifiedException e) {
				// TODO: infinite retrys?
				createResponse(tag, url);
			}
		}
	}

	@PreAuthorize("@auth.isLoggedIn() and @auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void deleteResponse(String tag, String url) {
		var ref = tagger.getResponseRef(auth.getUserTag().tag, auth.getOrigin(), url);
		ref.removeTag(tag);
		try {
			ingest.updateResponse(auth.getOrigin(), ref);
		} catch (ModifiedException e) {
			// TODO: infinite retrys?
			deleteResponse(tag, url);
		}
	}

	@PreAuthorize("@auth.isLoggedIn() and @auth.hasRole('USER') and @auth.canPatchTags(#tags)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void respond(List<String> tags, String url, Patch patch) {
		var ref = tagger.getResponseRef(auth.getUserTag().tag, auth.getOrigin(), url);
		for (var tag : tags) {
			var newTag = !tag.startsWith("-") && !ref.hasTag(tag);
			ref.addTag(tag);
			if (newTag) {
				configs.getPlugin(tag, auth.getOrigin())
					.map(Plugin::getDefaults)
					.filter(defaults -> !defaults.isNull())
					.ifPresent(defaults -> ref.setPlugin(tag, defaults));
			}
		}
		if (patch != null) {
			try {
				var plugins = ref.getPlugins() == null ? validate.pluginDefaults(auth.getOrigin(), ref) : ref.getPlugins().deepCopy();
				var expandedTags = expandTags(ref.getTags());
				var initialized = new HashSet<String>();
				var patchNode = objectMapper.valueToTree(patch);
				if (patch instanceof JsonPatch) {
					var missingPlugins = new HashSet<String>();
					for (var tag : expandedTags) if (!plugins.hasNonNull(tag)) missingPlugins.add(tag);
					JsonNode patchedPlugins = plugins;
					var segment = objectMapper.createArrayNode();
					for (var operation : patchNode) {
						var placeholders = pluginPlaceholders(missingPlugins, operation, initialized);
						if (!placeholders.isEmpty()) {
							if (!segment.isEmpty()) patchedPlugins = JsonPatch.fromJson(segment).apply(patchedPlugins);
							segment = objectMapper.createArrayNode();
							if (patchedPlugins instanceof ObjectNode objectNode) {
								for (var placeholder : placeholders.entrySet()) {
									initializePlugin(objectNode, placeholder.getKey(), placeholder.getValue(), initialized);
								}
							}
						}
						segment.add(operation);
						missingPlugins.removeIf(tag -> touchesPluginRoot(operation, tag));
					}
					if (!segment.isEmpty()) patchedPlugins = JsonPatch.fromJson(segment).apply(patchedPlugins);
					if (!(patchedPlugins instanceof ObjectNode)) {
						throw new JsonPatchException("Plugin patch must produce an object");
					}
					plugins = (ObjectNode) patchedPlugins;
				} else {
					for (var tag : expandedTags) initializePlugin(plugins, tag, null, initialized);
					var patchedPlugins = patch.apply(plugins);
					if (!(patchedPlugins instanceof ObjectNode)) {
						throw new JsonPatchException("Plugin patch must produce an object");
					}
					plugins = (ObjectNode) patchedPlugins;
				}
				ref.addPlugins(expandedTags, plugins, patchNode, initialized);
			} catch (IOException | JsonPatchException e) {
				throw new InvalidPatchException("Ref " + auth.getOrigin() + " " + url, e);
			}
		}
		try {
			ingest.updateResponse(auth.getOrigin(), ref);
		} catch (ModifiedException e) {
			// TODO: infinite retrys?
			respond(tags, url, patch);
		}
	}

	private Map<String, String> pluginPlaceholders(HashSet<String> tags, JsonNode operation, HashSet<String> initialized) {
		var result = new HashMap<String, String>();
		var op = operation.path("op").asText();
		if (!op.equals("add") && !op.equals("copy") && !op.equals("move")) return result;
		for (var tag : tags) {
			if (initialized.contains(tag)) continue;
			var path = Ref.pluginPointer(tag);
			var operationPath = operation.path("path").asText();
			if (!operationPath.startsWith(path + "/")) continue;
			if (!op.equals("add")) {
				var from = operation.path("from").asText();
				if (from.isEmpty() || from.equals(path) || from.startsWith(path + "/")) continue;
			}
			var key = operationPath.substring(path.length() + 1);
			result.put(tag, key.contains("/") ? key.substring(0, key.indexOf("/")) : key);
		}
		return result;
	}

	static boolean touchesPluginRoot(JsonNode operation, String tag) {
		var op = operation.path("op").asText();
		if (op.equals("test")) return false;
		var path = Ref.pluginPointer(tag);
		var operationPath = operation.path("path").asText();
		if (operationPath.isEmpty() || operationPath.equals(path)) return true;
		return op.equals("move") && operation.path("from").asText().equals(path);
	}

	private void initializePlugin(ObjectNode plugins, String tag, String key, HashSet<String> initialized) {
		if (initialized.contains(tag) || plugins.hasNonNull(tag)) return;
		configs.getPlugin(tag, auth.getOrigin())
			.filter(plugin -> plugin.getSchema() != null)
			.ifPresent(plugin -> {
				var schema = plugin.getSchema();
				var placeholder = pluginPlaceholder(schema, schema, key, new HashSet<>());
				if (placeholder == null) return;
				plugins.set(tag, placeholder);
				initialized.add(tag);
			});
	}

	private JsonNode pluginPlaceholder(JsonNode schema, ObjectNode root, String key, HashSet<String> refs) {
		if (!schema.isObject()) return null;
		if (schema.has("elements")) return objectMapper.createArrayNode();
		if (schema.has("properties") || schema.has("optionalProperties") || schema.has("values") || schema.has("discriminator")) {
			return objectMapper.createObjectNode();
		}
		if (schema.has("ref")) {
			var ref = schema.path("ref").asText();
			return refs.add(ref) ? pluginPlaceholder(root.path("definitions").path(ref), root, key, refs) : null;
		}
		if (key == null || schema.has("type") || schema.has("enum")) return null;
		return key.equals("-") || key.matches("\\d+") ? objectMapper.createArrayNode() : objectMapper.createObjectNode();
	}
}
