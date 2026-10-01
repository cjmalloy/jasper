package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.errors.InvalidPatchException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithMockUser("+user/tester")
@IntegrationTest
public class TaggingServiceIT {

	@Autowired
	TaggingService taggingService;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ConfigCache configCache;

	static final String URL = "https://www.example.com/";

	Ref refWithTags(String url, String... tags) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setTags(new ArrayList<>(List.of(tags)));
		refRepository.save(ref);
		return ref;
	}

	Plugin schemaPlugin(String tag) throws IOException {
		var plugin = new Plugin();
		plugin.setTag(tag);
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		return pluginRepository.save(plugin);
	}

	@BeforeEach
	void init() {
		configCache.clearUserCache();
		configCache.clearPluginCache();
		configCache.clearTemplateCache();
		refRepository.deleteAll();
		pluginRepository.deleteAll();
	}

	@Test
	void testCreateTagRef() {
		refWithTags(URL, "+user/tester");

		taggingService.create("test", URL, "");

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

	@Test
	void testCreatePrivateTagRef() {
		refWithTags(URL, "+user/tester");

		assertThatThrownBy(() -> taggingService.create("_test", URL, ""))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.doesNotContain("_test");
	}

	@Test
	void testDeletePrivateTagRef() {
		refWithTags(URL, "+user/tester", "_test");

		assertThatThrownBy(() -> taggingService.delete("_test", URL, ""))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("_test");
	}

	@Test
	void testCreateTagRefUnauthorized() {
		refWithTags(URL, "public");

		assertThatThrownBy(() -> taggingService.create("test", URL, ""))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"EDITOR"})
	void testCreateTagRefEditor() {
		refWithTags(URL, "public");

		taggingService.create("test", URL, "");

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondBasic() {
		refWithTags(URL, "+user/tester");

		taggingService.respond(List.of("test"), URL, null);

		var responseUrl = "tag:/user/tester?url=" + URL;
		assertThat(refRepository.existsByUrlAndOrigin(responseUrl, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getTags())
			.contains("test", "internal", "+user/tester");
		assertThat(fetched.getSources())
			.contains(URL);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondMultipleTags() {
		refWithTags(URL, "+user/tester");

		taggingService.respond(List.of("tag1", "tag2", "tag3"), URL, null);

		var responseUrl = "tag:/user/tester?url=" + URL;
		assertThat(refRepository.existsByUrlAndOrigin(responseUrl, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getTags())
			.contains("tag1", "tag2", "tag3", "internal", "+user/tester");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithPluginDefaults() throws IOException {
		refWithTags(URL, "+user/tester");

		// Create a plugin with defaults and schema
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		var defaults = (ObjectNode) objectMapper.readTree("{\"color\": \"blue\", \"size\": 10}");
		plugin.setDefaults(defaults);
		var schema = (ObjectNode) objectMapper.readTree("""
		{
			"properties": {
				"color": { "type": "string" },
				"size": { "type": "uint32" }
			}
		}""");
		plugin.setSchema(schema);
		pluginRepository.save(plugin);

		taggingService.respond(List.of("plugin/test"), URL, null);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getTags())
			.contains("plugin/test");
		assertThat(fetched.getPlugins())
			.isNotNull();
		assertThat(fetched.getPlugins().has("plugin/test"))
			.isTrue();
		var pluginData = fetched.getPlugins().get("plugin/test");
		assertThat(pluginData.get("color").asText())
			.isEqualTo("blue");
		assertThat(pluginData.get("size").asInt())
			.isEqualTo(10);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatch() throws IOException {
		refWithTags(URL, "+user/tester");

		// Create a plugin with defaults and schema
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		var defaults = (ObjectNode) objectMapper.readTree("{\"color\": \"blue\", \"size\": 10}");
		plugin.setDefaults(defaults);
		var schema = (ObjectNode) objectMapper.readTree("""
		{
			"properties": {
				"color": { "type": "string" },
				"size": { "type": "uint32" }
			}
		}""");
		plugin.setSchema(schema);
		pluginRepository.save(plugin);

		// Create a JSON patch to modify the plugin data
		var patchJson = "[{\"op\": \"replace\", \"path\": \"/plugin~1test/color\", \"value\": \"red\"}]";
		var patch = objectMapper.readValue(patchJson, JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins())
			.isNotNull();
		assertThat(fetched.getPlugins().has("plugin/test"))
			.isTrue();
		var pluginData = fetched.getPlugins().get("plugin/test");
		assertThat(pluginData.get("color").asText())
			.isEqualTo("red");
		assertThat(pluginData.get("size").asInt())
			.isEqualTo(10);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchAdd() throws IOException {
		refWithTags(URL, "+user/tester");

		// Create a plugin with defaults and schema
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		var defaults = (ObjectNode) objectMapper.readTree("{\"color\": \"blue\"}");
		plugin.setDefaults(defaults);
		var schema = (ObjectNode) objectMapper.readTree("""
		{
			"properties": {
				"color": { "type": "string" },
				"newField": { "type": "string" }
			}
		}""");
		plugin.setSchema(schema);
		pluginRepository.save(plugin);

		// Create a JSON patch to add a new field
		var patchJson = "[{\"op\": \"add\", \"path\": \"/plugin~1test/newField\", \"value\": \"newValue\"}]";
		var patch = objectMapper.readValue(patchJson, JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		var pluginData = fetched.getPlugins().get("plugin/test");
		assertThat(pluginData.get("color").asText())
			.isEqualTo("blue");
		assertThat(pluginData.get("newField").asText())
			.isEqualTo("newValue");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchWhenPluginDataDoesNotExist() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"properties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var untouchedPlugin = new Plugin();
		untouchedPlugin.setTag("plugin/untouched");
		untouchedPlugin.setOrigin("");
		untouchedPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(untouchedPlugin);

		var patch = objectMapper.readValue("""
		[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test", "plugin/untouched"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test").get("color").asText())
			.isEqualTo("red");
		assertThat(fetched.getPlugins().has("plugin/untouched"))
			.isFalse();
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchWhenExpandedPluginDataIsNull() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var untouchedPlugin = new Plugin();
		untouchedPlugin.setTag("plugin/untouched");
		untouchedPlugin.setOrigin("");
		untouchedPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(untouchedPlugin);

		taggingService.respond(List.of("plugin/test/sub", "plugin/untouched"), URL, null);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var response = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		response.setPlugins((ObjectNode) objectMapper.readTree("""
		{
			"plugin/test": null,
			"plugin/untouched": null
		}"""));
		refRepository.saveAndFlush(response);

		var patch = objectMapper.readValue("""
		[
			{"op": "test", "path": "/plugin~1test", "value": null},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test/sub", "plugin/untouched"), URL, patch);

		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test").get("color").asText())
			.isEqualTo("red");
		assertThat(fetched.getPlugins().has("plugin/untouched"))
			.isFalse();
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchDoesNotExposeAbsentPluginPlaceholder() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var patch = objectMapper.readValue("""
		[{"op": "test", "path": "/plugin~1test", "value": {}}]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchDoesNotExposePlaceholderAsCopySource() throws IOException {
		refWithTags(URL, "+user/tester");

		var source = new Plugin();
		source.setTag("plugin/source");
		source.setOrigin("");
		source.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"value": { "type": "string" }
			}
		}"""));
		pluginRepository.save(source);

		var target = new Plugin();
		target.setTag("plugin/target");
		target.setOrigin("");
		target.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"value": {}
			}
		}"""));
		pluginRepository.save(target);

		var patch = objectMapper.readValue("""
		[{"op": "copy", "from": "/plugin~1source", "path": "/plugin~1target/value"}]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/source", "plugin/target"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchDoesNotExposePlaceholderToRootCopySource() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"value": {}
			}
		}"""));
		pluginRepository.save(plugin);

		var patch = objectMapper.readValue("""
		[{"op": "copy", "from": "", "path": "/plugin~1test/value"}]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchReplacingRootWithNonObjectIntermediate() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var patch = objectMapper.readValue("""
		[
			{"op": "replace", "path": "", "value": []},
			{"op": "replace", "path": "", "value": {"plugin/test": {}}},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test"))
			.isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchReplacingRootWithNonObject() throws IOException {
		refWithTags(URL, "+user/tester");

		var patch = objectMapper.readValue("""
		[{"op": "replace", "path": "", "value": []}]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchForEmptyFormSchemas() throws IOException {
		refWithTags(URL, "+user/tester");

		var objectPlugin = new Plugin();
		objectPlugin.setTag("plugin/object");
		objectPlugin.setOrigin("");
		objectPlugin.setSchema((ObjectNode) objectMapper.readTree("{}"));
		pluginRepository.save(objectPlugin);

		var arrayPlugin = new Plugin();
		arrayPlugin.setTag("plugin/array");
		arrayPlugin.setOrigin("");
		arrayPlugin.setSchema((ObjectNode) objectMapper.readTree("{\"nullable\": true}"));
		pluginRepository.save(arrayPlugin);

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1object/color", "value": "red"},
			{"op": "add", "path": "/plugin~1array/0", "value": "red"}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/object", "plugin/array"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/object"))
			.isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
		assertThat(fetched.getPlugins().get("plugin/array"))
			.isEqualTo(objectMapper.readTree("[\"red\"]"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchDoesNotReinitializeRemovedPlugin() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setDefaults((ObjectNode) objectMapper.readTree("{\"color\": \"blue\"}"));
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);
		taggingService.respond(List.of("plugin/test"), URL, null);

		var patch = objectMapper.readValue("""
		[
			{"op": "remove", "path": "/plugin~1test"},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchInitializesPluginOnlyOnce() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1test/color", "value": "blue"},
			{"op": "remove", "path": "/plugin~1test"},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchWhenArrayPluginDataDoesNotExist() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"elements": { "type": "string" }
		}"""));
		pluginRepository.save(plugin);

		var patch = objectMapper.readValue("""
		[{"op": "add", "path": "/plugin~1test/0", "value": "red"}]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test"))
			.isEqualTo(objectMapper.readTree("[\"red\"]"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchForOtherObjectAndReferencedSchemas() throws IOException {
		refWithTags(URL, "+user/tester");

		var valuesPlugin = new Plugin();
		valuesPlugin.setTag("plugin/values");
		valuesPlugin.setOrigin("");
		valuesPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"values": { "type": "string" }
		}"""));
		pluginRepository.save(valuesPlugin);

		var discriminatorPlugin = new Plugin();
		discriminatorPlugin.setTag("plugin/discriminator");
		discriminatorPlugin.setOrigin("");
		discriminatorPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"discriminator": "kind",
			"mapping": {
				"test": {
					"properties": {
						"value": { "type": "string" }
					}
				}
			}
		}"""));
		pluginRepository.save(discriminatorPlugin);

		var refObjectPlugin = new Plugin();
		refObjectPlugin.setTag("plugin/ref.object");
		refObjectPlugin.setOrigin("");
		refObjectPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"definitions": {
				"data": { "values": { "type": "string" } }
			},
			"ref": "data"
		}"""));
		pluginRepository.save(refObjectPlugin);

		var refArrayPlugin = new Plugin();
		refArrayPlugin.setTag("plugin/ref.array");
		refArrayPlugin.setOrigin("");
		refArrayPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"definitions": {
				"data": { "elements": { "type": "string" } }
			},
			"ref": "data"
		}"""));
		pluginRepository.save(refArrayPlugin);

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1values/key", "value": "value"},
			{"op": "add", "path": "/plugin~1discriminator/kind", "value": "test"},
			{"op": "add", "path": "/plugin~1discriminator/value", "value": "value"},
			{"op": "add", "path": "/plugin~1ref.object/key", "value": "value"},
			{"op": "add", "path": "/plugin~1ref.array/0", "value": "value"}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/values", "plugin/discriminator", "plugin/ref.object", "plugin/ref.array"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/values"))
			.isEqualTo(objectMapper.readTree("{\"key\":\"value\"}"));
		assertThat(fetched.getPlugins().get("plugin/discriminator"))
			.isEqualTo(objectMapper.readTree("{\"kind\":\"test\",\"value\":\"value\"}"));
		assertThat(fetched.getPlugins().get("plugin/ref.object"))
			.isEqualTo(objectMapper.readTree("{\"key\":\"value\"}"));
		assertThat(fetched.getPlugins().get("plugin/ref.array"))
			.isEqualTo(objectMapper.readTree("[\"value\"]"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchAddingInitializedPlugins() throws IOException {
		refWithTags(URL, "+user/tester");

		var scalarPlugin = new Plugin();
		scalarPlugin.setTag("plugin/scalar");
		scalarPlugin.setOrigin("");
		scalarPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"type": "string"
		}"""));
		pluginRepository.save(scalarPlugin);

		var arrayPlugin = new Plugin();
		arrayPlugin.setTag("plugin/array");
		arrayPlugin.setOrigin("");
		arrayPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"elements": { "type": "string" }
		}"""));
		pluginRepository.save(arrayPlugin);

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1scalar", "value": "red"},
			{"op": "add", "path": "/plugin~1array", "value": []}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/scalar", "plugin/array"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/scalar"))
			.isEqualTo(objectMapper.readTree("\"red\""));
		assertThat(fetched.getPlugins().get("plugin/array"))
			.isEqualTo(objectMapper.readTree("[]"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonMergePatchWhenPluginDataDoesNotExist() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"properties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var untouchedPlugin = new Plugin();
		untouchedPlugin.setTag("plugin/untouched");
		untouchedPlugin.setOrigin("");
		untouchedPlugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(untouchedPlugin);

		var patch = objectMapper.readValue("""
		{"plugin/test": {"color": "red"}}
		""", JsonMergePatch.class);

		taggingService.respond(List.of("plugin/test", "plugin/untouched"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test").get("color").asText())
			.isEqualTo("red");
		assertThat(fetched.getPlugins().has("plugin/untouched"))
			.isFalse();
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondRejectsJsonMergePatchArray() throws IOException {
		refWithTags(URL, "+user/tester");

		var patch = objectMapper.readValue("""
		[{"op": "add", "path": "/plugin~1test", "value": {}}]
		""", JsonMergePatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithInvalidJsonPatch() throws IOException {
		refWithTags(URL, "+user/tester");

		// Create an invalid JSON patch that references a non-existent path
		var patchJson = "[{\"op\": \"replace\", \"path\": \"/nonexistent/field\", \"value\": \"test\"}]";
		var patch = objectMapper.readValue(patchJson, JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/anonymous", roles = {"USER"})
	void testRespondUnauthorized() {
		refWithTags(URL, "+user/tester");

		// User without permission to patch tags should fail
		assertThatThrownBy(() -> taggingService.respond(List.of("_private"), URL, null))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithoutPlugin() {
		refWithTags(URL, "+user/tester");

		// Respond with a tag that has no plugin configured
		taggingService.respond(List.of("plugin/nonexistent"), URL, null);

		var responseUrl = "tag:/user/tester?url=" + URL;
		assertThat(refRepository.existsByUrlAndOrigin(responseUrl, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getTags())
			.contains("plugin/nonexistent");
		// Plugin data should not be set if plugin has no defaults
		if (fetched.getPlugins() != null) {
			assertThat(fetched.getPlugins().has("plugin/nonexistent"))
				.isFalse();
		}
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchAddingNullToSchemalessPlugin() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1test", "value": null}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getTags())
			.contains("plugin/test");
		if (fetched.getPlugins() != null) {
			assertThat(fetched.getPlugins().has("plugin/test"))
				.isFalse();
		}
	}

	void schemalessPlugin(String defaults) {
		if (defaults.equals("missing")) return;
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		if (defaults.equals("nullNode")) plugin.setDefaults(objectMapper.nullNode());
		pluginRepository.save(plugin);
	}

	void assertNoSchemalessPluginData() {
		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getTags())
			.contains("plugin/test");
		if (fetched.getPlugins() != null) {
			assertThat(fetched.getPlugins().has("plugin/test"))
				.isFalse();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "null", "nullNode"})
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithSchemalessPluginWithoutPatch(String defaults) {
		refWithTags(URL, "+user/tester");
		schemalessPlugin(defaults);

		taggingService.respond(List.of("plugin/test"), URL, null);

		assertNoSchemalessPluginData();
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "null", "nullNode"})
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithSchemalessPluginUntouchedByJsonPatch(String defaults) throws IOException {
		refWithTags(URL, "+user/tester");
		schemalessPlugin(defaults);
		var other = new Plugin();
		other.setTag("plugin/other");
		other.setOrigin("");
		other.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(other);

		var patch = objectMapper.readValue("""
		[{"op": "add", "path": "/plugin~1other/color", "value": "red"}]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test", "plugin/other"), URL, patch);

		assertNoSchemalessPluginData();
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "null", "nullNode"})
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithSchemalessPluginUntouchedByJsonMergePatch(String defaults) throws IOException {
		refWithTags(URL, "+user/tester");
		schemalessPlugin(defaults);
		var other = new Plugin();
		other.setTag("plugin/other");
		other.setOrigin("");
		other.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(other);

		var patch = objectMapper.readValue("""
		{"plugin/other": {"color": "red"}}
		""", JsonMergePatch.class);

		taggingService.respond(List.of("plugin/test", "plugin/other"), URL, patch);

		assertNoSchemalessPluginData();
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "null", "nullNode"})
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonMergePatchSettingSchemalessPluginNull(String defaults) throws IOException {
		refWithTags(URL, "+user/tester");
		schemalessPlugin(defaults);

		var patch = objectMapper.readValue("""
		{"plugin/test": null}
		""", JsonMergePatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		assertNoSchemalessPluginData();
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "null", "nullNode"})
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondRejectsNestedJsonPatchIntoSchemalessPlugin(String defaults) throws IOException {
		refWithTags(URL, "+user/tester");
		schemalessPlugin(defaults);

		var patch = objectMapper.readValue("""
		[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchForSchemaPluginWithNullDefaults() throws IOException {
		refWithTags(URL, "+user/tester");

		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		plugin.setDefaults(objectMapper.nullNode());
		plugin.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"optionalProperties": {
				"color": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var patch = objectMapper.readValue("""
		[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test").get("color").asText())
			.isEqualTo("red");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondMultiplePluginsWithDefaults() throws IOException {
		refWithTags(URL, "+user/tester");

		// Create multiple plugins with defaults and schemas
		var plugin1 = new Plugin();
		plugin1.setTag("plugin/test1");
		plugin1.setOrigin("");
		plugin1.setDefaults((ObjectNode) objectMapper.readTree("{\"value1\": \"a\"}"));
		plugin1.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"properties": {
				"value1": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin1);

		var plugin2 = new Plugin();
		plugin2.setTag("plugin/test2");
		plugin2.setOrigin("");
		plugin2.setDefaults((ObjectNode) objectMapper.readTree("{\"value2\": \"b\"}"));
		plugin2.setSchema((ObjectNode) objectMapper.readTree("""
		{
			"properties": {
				"value2": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin2);

		taggingService.respond(List.of("plugin/test1", "plugin/test2"), URL, null);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().has("plugin/test1"))
			.isTrue();
		assertThat(fetched.getPlugins().has("plugin/test2"))
			.isTrue();
		assertThat(fetched.getPlugins().get("plugin/test1").get("value1").asText())
			.isEqualTo("a");
		assertThat(fetched.getPlugins().get("plugin/test2").get("value2").asText())
			.isEqualTo("b");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchDoesNotReplacePatchWrittenNull() throws IOException {
		refWithTags(URL, "+user/tester");
		schemaPlugin("plugin/test");

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1test", "value": null},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchDoesNotInitializeAfterRootReplace() throws IOException {
		refWithTags(URL, "+user/tester");
		schemaPlugin("plugin/test");

		var patch = objectMapper.readValue("""
		[
			{"op": "replace", "path": "", "value": {}},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchDoesNotInitializeAfterMoveAway() throws IOException {
		refWithTags(URL, "+user/tester");
		schemaPlugin("plugin/test");
		schemaPlugin("plugin/other");

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1test", "value": null},
			{"op": "move", "from": "/plugin~1test", "path": "/plugin~1other"},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		assertThatThrownBy(() -> taggingService.respond(List.of("plugin/test", "plugin/other"), URL, patch))
			.isInstanceOf(InvalidPatchException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchRootWriteMatchesExactTag() throws IOException {
		refWithTags(URL, "+user/tester");
		schemaPlugin("plugin/test");
		schemaPlugin("plugin/test2");

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1test2", "value": null},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test", "plugin/test2"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test"))
			.isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
		assertThat(fetched.getPlugins().has("plugin/test2")).isFalse();
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchTestOperationDoesNotChangeEligibility() throws IOException {
		refWithTags(URL, "+user/tester");
		schemaPlugin("plugin/test");

		var patch = objectMapper.readValue("""
		[
			{"op": "test", "path": "", "value": {}},
			{"op": "add", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test"))
			.isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithJsonPatchNestedWriteDoesNotChangeEligibility() throws IOException {
		refWithTags(URL, "+user/tester");
		schemaPlugin("plugin/test");

		var patch = objectMapper.readValue("""
		[
			{"op": "add", "path": "/plugin~1test/color", "value": "blue"},
			{"op": "replace", "path": "/plugin~1test/color", "value": "red"}
		]
		""", JsonPatch.class);

		taggingService.respond(List.of("plugin/test"), URL, patch);

		var responseUrl = "tag:/user/tester?url=" + URL;
		var fetched = refRepository.findOneByUrlAndOrigin(responseUrl, "").get();
		assertThat(fetched.getPlugins().get("plugin/test"))
			.isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
	}
}
