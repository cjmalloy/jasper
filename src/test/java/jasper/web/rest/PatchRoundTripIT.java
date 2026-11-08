package jasper.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH round-trips with both application/merge-patch+json and application/json-patch+json.
 * Changing one field must leave every other field, including nested JSON trees, unchanged.
 */
@AutoConfigureMockMvc
@IntegrationTest
class PatchRoundTripIT {
	static final String MERGE = "application/merge-patch+json";
	static final String JSON_PATCH = "application/json-patch+json";
	static final String URL = "https://www.example.com/patch";

	/**
	 * Nested objects, arrays, ints, longs, floats, booleans, strings, nulls and empty containers.
	 */
	static final String RICH = """
		{
			"count": 42,
			"big": 12345678901,
			"ratio": 1.5,
			"flag": true,
			"off": false,
			"text": "héllo \\"quoted\\"",
			"none": null,
			"empty": {},
			"emptyList": [],
			"list": [1, 2.25, -3, true, "x", null, {"a": [ {"b": false} ]}],
			"nested": { "deep": { "deeper": { "n": 0, "neg": -7.75, "arr": [[1], [2, [3]]] } } }
		}""";

	static final String RICH_SCHEMA = """
		{
			"properties": {
				"count": { "type": "int32" },
				"big": { "type": "float64" },
				"ratio": { "type": "float64" },
				"flag": { "type": "boolean" },
				"off": { "type": "boolean" },
				"text": { "type": "string" },
				"none": {},
				"empty": {},
				"emptyList": { "elements": {} },
				"list": { "elements": {} },
				"nested": {}
			}
		}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	Props props;

	@Autowired
	ConfigCache configCache;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	TemplateRepository templateRepository;

	@BeforeEach
	void init() {
		cleanup();
		// The default user is used for requests, so set the admin user by header
		props.setAllowUserTagHeader(true);
		props.setAllowUserRoleHeader(true);
	}

	@AfterEach
	void cleanup() {
		props.setAllowUserTagHeader(false);
		props.setAllowUserRoleHeader(false);
		refRepository.deleteAll();
		extRepository.deleteAll();
		userRepository.deleteAll();
		pluginRepository.deleteAll();
		templateRepository.deleteAll();
		configCache.clearUserCache();
		configCache.clearPluginCache();
		configCache.clearTemplateCache();
		configCache.clearConfigCache();
	}

	JsonNode json(String json) throws Exception {
		return mapper.readTree(json);
	}

	MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder req) {
		return req
			.header("User-Tag", "+user/tester")
			.header("User-Role", "ROLE_ADMIN")
			.with(csrf().asHeader());
	}

	void create(String api, String body) throws Exception {
		mockMvc
			.perform(admin(post(api))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
	}

	ObjectNode read(String api, MultiValueMap<String, String> id) throws Exception {
		return (ObjectNode) mapper.readTree(mockMvc
			.perform(admin(get(api)).params(id))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
	}

	MultiValueMap<String, String> tag(String tag) {
		var result = new LinkedMultiValueMap<String, String>();
		result.add("tag", tag);
		return result;
	}

	MultiValueMap<String, String> url(String url) {
		var result = new LinkedMultiValueMap<String, String>();
		result.add("url", url);
		result.add("origin", "");
		return result;
	}

	/**
	 * Patch {@code field} and check that nothing else changed.
	 * {@code modified} is rewritten to the save time and returned as the new cursor.
	 */
	ObjectNode roundTrip(String api, MultiValueMap<String, String> id, String contentType, String body, String field) throws Exception {
		var before = read(api, id);
		var cursor = Instant.parse(before.get("modified").asText());
		var response = mockMvc
			.perform(admin(patch(api))
				.params(id)
				.param("cursor", cursor.toString())
				.contentType(contentType)
				.content(body))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var after = read(api, id);

		var modified = Instant.parse(after.get("modified").asText());
		assertThat(modified).isAfter(cursor);
		assertThat(mapper.readValue(response, Instant.class)).isCloseTo(modified, within(1, ChronoUnit.MICROS));
		assertThat(after.get(field)).isNotEqualTo(before.get(field));

		var expected = before.deepCopy();
		expected.remove("modified");
		expected.remove(field);
		var actual = after.deepCopy();
		actual.remove("modified");
		actual.remove(field);
		assertThat(actual).isEqualTo(expected);
		return after;
	}

	// Ext

	void createExt() throws Exception {
		create("/api/v1/template", """
			{ "tag": "rt", "schema": %s }""".formatted(RICH_SCHEMA));
		configCache.clearTemplateCache();
		create("/api/v1/ext", """
			{ "tag": "rt", "name": "Ext", "config": %s }""".formatted(RICH));
	}

	void assertExt(ObjectNode ext) throws Exception {
		assertThat(ext.get("tag").asText()).isEqualTo("rt");
		assertThat(ext.has("origin")).isFalse();
		assertThat(ext.get("config")).isEqualTo(json(RICH));
		assertThat(ext.properties().stream().map(e -> e.getKey()).toList())
			.containsExactlyInAnyOrder("tag", "name", "config", "modified");
	}

	@Test
	void testMergePatchExt() throws Exception {
		createExt();

		var after = roundTrip("/api/v1/ext", tag("rt"), MERGE, """
			{ "name": "Merged" }""", "name");

		assertThat(after.get("name").asText()).isEqualTo("Merged");
		assertExt(after);
	}

	@Test
	void testJsonPatchExt() throws Exception {
		createExt();

		var after = roundTrip("/api/v1/ext", tag("rt"), JSON_PATCH, """
			[{ "op": "replace", "path": "/name", "value": "Patched" }]""", "name");

		assertThat(after.get("name").asText()).isEqualTo("Patched");
		assertExt(after);
	}

	@Test
	void testMergePatchExtNestedConfig() throws Exception {
		createExt();

		var after = roundTrip("/api/v1/ext", tag("rt"), MERGE, """
			{ "config": { "nested": { "deep": { "deeper": { "n": 1 } } } } }""", "config");

		var expected = (ObjectNode) json(RICH);
		((ObjectNode) expected.at("/nested/deep/deeper")).put("n", 1);
		assertThat(after.get("config")).isEqualTo(expected);
		assertThat(after.get("name").asText()).isEqualTo("Ext");
	}

	// User

	void createUser() throws Exception {
		create("/api/v1/user", """
			{
				"tag": "+user/alice",
				"name": "Alice",
				"role": "ROLE_USER",
				"readAccess": ["public", "+user/bob"],
				"writeAccess": ["+user/alice/notes"],
				"tagReadAccess": ["news"],
				"tagWriteAccess": ["+user/alice"],
				"authorizedKeys": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl alice@host",
				"external": { "ids": ["ext-1", "ext-2"] }
			}""");
		mockMvc
			.perform(admin(post("/api/v1/user/keygen"))
				.param("tag", "+user/alice"))
			.andExpect(status().isOk());
	}

	void assertUser(ObjectNode user) {
		assertThat(user.get("tag").asText()).isEqualTo("+user/alice");
		assertThat(user.get("readAccess")).hasToString("[\"public\",\"+user/bob\"]");
		assertThat(user.get("writeAccess")).hasToString("[\"+user/alice/notes\"]");
		assertThat(user.get("tagReadAccess")).hasToString("[\"news\"]");
		assertThat(user.get("tagWriteAccess")).hasToString("[\"+user/alice\"]");
		assertThat(user.get("external")).hasToString("{\"ids\":[\"ext-1\",\"ext-2\"]}");
		// pubKey is a byte[] rendered as base64 of the "ssh-rsa ... +user/alice" line
		assertThat(new String(Base64.getDecoder().decode(user.get("pubKey").asText())))
			.startsWith("ssh-rsa ")
			.endsWith(" +user/alice");
		// The private key is never returned
		assertThat(user.has("key")).isFalse();
		assertThat(user.properties().stream().map(e -> e.getKey()).toList())
			.containsExactlyInAnyOrder("tag", "name", "role", "readAccess", "writeAccess", "tagReadAccess", "tagWriteAccess", "modified", "pubKey", "authorizedKeys", "external");
	}

	@Test
	void testMergePatchUserRole() throws Exception {
		createUser();
		var key = userRepository.findOneByQualifiedTag("+user/alice").orElseThrow().getKey();

		var after = roundTrip("/api/v1/user", tag("+user/alice"), MERGE, """
			{ "role": "ROLE_EDITOR" }""", "role");

		assertThat(after.get("role").asText()).isEqualTo("ROLE_EDITOR");
		assertThat(after.get("name").asText()).isEqualTo("Alice");
		assertUser(after);
		assertThat(userRepository.findOneByQualifiedTag("+user/alice").orElseThrow().getKey()).isEqualTo(key);
	}

	@Test
	void testJsonPatchUserAuthorizedKeys() throws Exception {
		createUser();
		var key = userRepository.findOneByQualifiedTag("+user/alice").orElseThrow().getKey();

		var after = roundTrip("/api/v1/user", tag("+user/alice"), JSON_PATCH, """
			[{ "op": "replace", "path": "/authorizedKeys", "value": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIA alice@other" }]""", "authorizedKeys");

		assertThat(after.get("authorizedKeys").asText()).isEqualTo("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIA alice@other");
		assertThat(after.get("role").asText()).isEqualTo("ROLE_USER");
		assertUser(after);
		assertThat(userRepository.findOneByQualifiedTag("+user/alice").orElseThrow().getKey()).isEqualTo(key);
	}

	// Plugin

	void createPlugin() throws Exception {
		create("/api/v1/plugin", """
			{ "tag": "plugin/rt", "name": "Plugin", "config": %s, "defaults": %s, "schema": %s }"""
			.formatted(RICH, RICH, RICH_SCHEMA));
	}

	void assertPlugin(ObjectNode plugin) throws Exception {
		assertThat(plugin.get("tag").asText()).isEqualTo("plugin/rt");
		assertThat(plugin.get("config")).isEqualTo(json(RICH));
		assertThat(plugin.get("defaults")).isEqualTo(json(RICH));
		assertThat(plugin.get("schema")).isEqualTo(json(RICH_SCHEMA));
		assertThat(plugin.properties().stream().map(e -> e.getKey()).toList())
			.containsExactlyInAnyOrder("tag", "name", "config", "defaults", "schema", "modified");
	}

	@Test
	void testMergePatchPlugin() throws Exception {
		createPlugin();

		var after = roundTrip("/api/v1/plugin", tag("plugin/rt"), MERGE, """
			{ "name": "Merged" }""", "name");

		assertThat(after.get("name").asText()).isEqualTo("Merged");
		assertPlugin(after);
	}

	@Test
	void testJsonPatchPlugin() throws Exception {
		createPlugin();

		var after = roundTrip("/api/v1/plugin", tag("plugin/rt"), JSON_PATCH, """
			[{ "op": "replace", "path": "/name", "value": "Patched" }]""", "name");

		assertThat(after.get("name").asText()).isEqualTo("Patched");
		assertPlugin(after);
	}

	// Template

	void createTemplate() throws Exception {
		create("/api/v1/template", """
			{ "tag": "rt", "name": "Template", "config": %s, "defaults": %s, "schema": %s }"""
			.formatted(RICH, RICH, RICH_SCHEMA));
	}

	void assertTemplate(ObjectNode template) throws Exception {
		assertThat(template.get("tag").asText()).isEqualTo("rt");
		assertThat(template.get("config")).isEqualTo(json(RICH));
		assertThat(template.get("defaults")).isEqualTo(json(RICH));
		assertThat(template.get("schema")).isEqualTo(json(RICH_SCHEMA));
		assertThat(template.properties().stream().map(e -> e.getKey()).toList())
			.containsExactlyInAnyOrder("tag", "name", "config", "defaults", "schema", "modified");
	}

	@Test
	void testMergePatchTemplate() throws Exception {
		createTemplate();

		var after = roundTrip("/api/v1/template", tag("rt"), MERGE, """
			{ "name": "Merged" }""", "name");

		assertThat(after.get("name").asText()).isEqualTo("Merged");
		assertTemplate(after);
	}

	@Test
	void testJsonPatchTemplate() throws Exception {
		createTemplate();

		var after = roundTrip("/api/v1/template", tag("rt"), JSON_PATCH, """
			[{ "op": "replace", "path": "/name", "value": "Patched" }]""", "name");

		assertThat(after.get("name").asText()).isEqualTo("Patched");
		assertTemplate(after);
	}

	// Ref

	void createRef() throws Exception {
		createPlugin();
		configCache.clearPluginCache();
		create("/api/v1/ref", """
			{
				"url": "%s",
				"title": "Title",
				"comment": "Comment",
				"tags": ["public", "+user/tester", "plugin/rt"],
				"sources": ["https://www.example.com/source"],
				"alternateUrls": ["https://www.example.com/alt"],
				"published": "2020-01-02T03:04:05.123456Z",
				"plugins": { "plugin/rt": %s }
			}""".formatted(URL, RICH));
	}

	void assertRef(ObjectNode ref) throws Exception {
		assertThat(ref.get("url").asText()).isEqualTo(URL);
		assertThat(ref.get("comment").asText()).isEqualTo("Comment");
		assertThat(ref.get("tags")).hasToString("[\"public\",\"+user/tester\",\"plugin/rt\"]");
		assertThat(ref.get("sources")).hasToString("[\"https://www.example.com/source\"]");
		assertThat(ref.get("alternateUrls")).hasToString("[\"https://www.example.com/alt\"]");
		assertThat(ref.get("published").asText()).isEqualTo("2020-01-02T03:04:05.123456Z");
		assertThat(ref.get("plugins")).isEqualTo(mapper.createObjectNode().set("plugin/rt", json(RICH)));
		var created = Instant.parse(ref.get("created").asText());
		assertThat(created).isBefore(Instant.parse(ref.get("modified").asText()));
	}

	@Test
	void testMergePatchRef() throws Exception {
		createRef();
		var created = read("/api/v1/ref", url(URL)).get("created").asText();

		var after = roundTrip("/api/v1/ref", url(URL), MERGE, """
			{ "title": "Merged" }""", "title");

		assertThat(after.get("title").asText()).isEqualTo("Merged");
		assertThat(after.get("created").asText()).isEqualTo(created);
		assertRef(after);
	}

	@Test
	void testJsonPatchRef() throws Exception {
		createRef();
		var created = read("/api/v1/ref", url(URL)).get("created").asText();

		var after = roundTrip("/api/v1/ref", url(URL), JSON_PATCH, """
			[{ "op": "replace", "path": "/title", "value": "Patched" }]""", "title");

		assertThat(after.get("title").asText()).isEqualTo("Patched");
		assertThat(after.get("created").asText()).isEqualTo(created);
		assertRef(after);
	}
}
