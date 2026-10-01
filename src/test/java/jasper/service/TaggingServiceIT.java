package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.Patch;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

	static final String RESPONSE_URL = "tag:/user/tester?url=" + URL;

	void savePlugin(String tag, String schema, String defaults) throws IOException {
		var plugin = new Plugin();
		plugin.setTag(tag);
		plugin.setOrigin("");
		if (schema != null) plugin.setSchema((ObjectNode) objectMapper.readTree(schema));
		if (defaults != null) plugin.setDefaults(objectMapper.readTree(defaults));
		pluginRepository.save(plugin);
	}

	void saveSchemaPlugin(String tag) throws IOException {
		savePlugin(tag, "{\"optionalProperties\": {\"color\": {\"type\": \"string\"}}}", null);
	}

	JsonPatch jsonPatch(String json) throws IOException {
		return objectMapper.readValue(json, JsonPatch.class);
	}

	JsonNode storedPlugin(String tag) {
		return refRepository.findOneByUrlAndOrigin(RESPONSE_URL, "").get().getPlugin(tag);
	}

	void assertRespondFailsUnchanged(List<String> tags, Patch patch) {
		// Create the response Ref up front so we can check it is not modified
		taggingService.respond(List.of(), URL, null);
		var before = refRepository.findOneByUrlAndOrigin(RESPONSE_URL, "").get();

		assertThatThrownBy(() -> taggingService.respond(tags, URL, patch))
			.isInstanceOf(InvalidPatchException.class);

		var after = refRepository.findOneByUrlAndOrigin(RESPONSE_URL, "").get();
		assertThat(after.getTags()).isEqualTo(before.getTags());
		assertThat(after.getPlugins()).isEqualTo(before.getPlugins());
		assertThat(after.getModified()).isEqualTo(before.getModified());
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchNestedAddIntoSchemaPluginWithoutDefaults() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]"""));

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondMergePatchIntoSchemaPluginWithoutDefaults() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		taggingService.respond(List.of("plugin/test"), URL, objectMapper.readValue("""
			{"plugin/test": {"color": "red"}}""", JsonMergePatch.class));

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchDoesNotStoreUntouchedSeeds() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");
		saveSchemaPlugin("plugin/untouched");

		taggingService.respond(List.of("plugin/test", "plugin/untouched"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]"""));

		var fetched = refRepository.findOneByUrlAndOrigin(RESPONSE_URL, "").get();
		assertThat(fetched.getTags()).contains("plugin/test", "plugin/untouched");
		assertThat(fetched.getPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
		assertThat(fetched.hasPlugin("plugin/untouched")).isFalse();
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchIntoElementsSchema() throws IOException {
		refWithTags(URL, "+user/tester");
		savePlugin("plugin/test", "{\"elements\": {\"type\": \"string\"}}", null);

		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/0", "value": "red"}]"""));

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("[\"red\"]"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchIntoRefSchema() throws IOException {
		refWithTags(URL, "+user/tester");
		savePlugin("plugin/test", """
			{"definitions": {"colors": {"values": {"type": "string"}}}, "ref": "colors"}""", null);

		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]"""));

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchReplacesDefault() throws IOException {
		refWithTags(URL, "+user/tester");
		savePlugin("plugin/test", """
			{"optionalProperties": {"color": {"type": "string"}, "size": {"type": "int32"}}}""", """
			{"color": "blue", "size": 1}""");

		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "replace", "path": "/plugin~1test/color", "value": "red"}]"""));

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"red\", \"size\": 1}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchKeepsExistingData() throws IOException {
		refWithTags(URL, "+user/tester");
		savePlugin("plugin/test", """
			{"optionalProperties": {"color": {"type": "string"}, "size": {"type": "int32"}}}""", null);
		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/color", "value": "blue"}]"""));
		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"blue\"}"));

		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/size", "value": 2}]"""));

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"blue\", \"size\": 2}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchIntoStoredNull() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");
		var response = refWithTags(RESPONSE_URL, "internal", "+user/tester", "plugin/test");
		response.setSources(new ArrayList<>(List.of(URL)));
		response.setPlugins((ObjectNode) objectMapper.readTree("{\"plugin/test\": null}"));
		refRepository.save(response);

		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]"""));

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"red\"}"));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchWritingFillerValueIsTreatedAsAbsent() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		// A patch that writes exactly the filler value can't be told apart from an
		// untouched filler, so it is dropped. Validate then treats it as absent.
		taggingService.respond(List.of("plugin/test"), URL, jsonPatch("""
			[{"op": "add", "path": "/plugin~1test", "value": {}}]"""));

		var fetched = refRepository.findOneByUrlAndOrigin(RESPONSE_URL, "").get();
		assertThat(fetched.getTags()).contains("plugin/test");
		assertThat(fetched.hasPlugin("plugin/test")).isFalse();
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchAddNullThenNestedAddFails() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		assertRespondFailsUnchanged(List.of("plugin/test"), jsonPatch("""
			[
				{"op": "add", "path": "/plugin~1test", "value": null},
				{"op": "add", "path": "/plugin~1test/color", "value": "red"}
			]"""));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchRemoveThenNestedAddFails() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		assertRespondFailsUnchanged(List.of("plugin/test"), jsonPatch("""
			[
				{"op": "remove", "path": "/plugin~1test"},
				{"op": "add", "path": "/plugin~1test/color", "value": "red"}
			]"""));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchReplaceRootThenNestedAddFails() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		assertRespondFailsUnchanged(List.of("plugin/test"), jsonPatch("""
			[
				{"op": "replace", "path": "", "value": {}},
				{"op": "add", "path": "/plugin~1test/color", "value": "red"}
			]"""));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchMoveNullThenNestedAddFails() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		assertRespondFailsUnchanged(List.of("plugin/test"), jsonPatch("""
			[
				{"op": "add", "path": "/tmp", "value": null},
				{"op": "move", "from": "/tmp", "path": "/plugin~1test"},
				{"op": "add", "path": "/plugin~1test/color", "value": "red"}
			]"""));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchNonObjectResultFails() throws IOException {
		refWithTags(URL, "+user/tester");
		saveSchemaPlugin("plugin/test");

		assertRespondFailsUnchanged(List.of("plugin/test"), jsonPatch("""
			[{"op": "replace", "path": "", "value": []}]"""));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondPatchNestedAddIntoSchemalessPluginFails() throws IOException {
		refWithTags(URL, "+user/tester");
		savePlugin("plugin/test", null, null);

		assertRespondFailsUnchanged(List.of("plugin/test"), jsonPatch("""
			[{"op": "add", "path": "/plugin~1test/color", "value": "red"}]"""));
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondWithoutPatchStoresDefaults() throws IOException {
		refWithTags(URL, "+user/tester");
		savePlugin("plugin/test", "{\"optionalProperties\": {\"color\": {\"type\": \"string\"}}}", "{\"color\": \"blue\"}");

		taggingService.respond(List.of("plugin/test"), URL, null);

		assertThat(storedPlugin("plugin/test")).isEqualTo(objectMapper.readTree("{\"color\": \"blue\"}"));
	}

	static Stream<Arguments> schemalessDefaults() {
		return Stream.of(
			Arguments.of("missing", false, null),
			Arguments.of("null", true, null),
			Arguments.of("NullNode", true, NullNode.getInstance()));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("schemalessDefaults")
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testRespondSchemalessPluginStoresNoData(String name, boolean set, JsonNode defaults) {
		refWithTags(URL, "+user/tester");
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("");
		if (set) plugin.setDefaults(defaults);
		pluginRepository.save(plugin);

		taggingService.respond(List.of("plugin/test"), URL, null);

		var fetched = refRepository.findOneByUrlAndOrigin(RESPONSE_URL, "").get();
		assertThat(fetched.getTags()).contains("plugin/test");
		assertThat(fetched.getPlugins() == null || !fetched.getPlugins().has("plugin/test")).isTrue();
	}

}
