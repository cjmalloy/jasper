package jasper.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.errors.ValidationErrors;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.web.rest.errors.ErrorConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JTD schema validation of Ref plugins and Ext config through the REST API,
 * including the problem+json error body, truncation and defaults.
 */
@AutoConfigureMockMvc
@IntegrationTest
class SchemaValidationIT {

	static final String PLUGIN_SCHEMA = """
		{
			"properties": { "name": { "type": "string" }, "age": { "type": "uint32" } },
			"optionalProperties": { "labels": { "elements": { "type": "string" } }, "extra": { "type": "boolean" } }
		}""";
	static final String PLUGIN_DEFAULTS = """
		{ "name": "anon", "age": 0, "extra": true }""";
	static final String TEMPLATE_SCHEMA = """
		{
			"properties": { "title": { "type": "string" } },
			"optionalProperties": { "size": { "type": "int8" } }
		}""";
	static final String TEMPLATE_DEFAULTS = """
		{ "title": "Untitled", "size": 3 }""";

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
		pluginRepository.deleteAll();
		templateRepository.deleteAll();
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

	ResultActions postJson(String api, String body) throws Exception {
		var result = mockMvc
			.perform(admin(post(api))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
		configCache.clearPluginCache();
		configCache.clearTemplateCache();
		return result;
	}

	ObjectNode getJson(MockHttpServletRequestBuilder req) throws Exception {
		return (ObjectNode) mapper.readTree(mockMvc
			.perform(admin(req))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
	}

	void createPlugin() throws Exception {
		postJson("/api/v1/plugin", """
			{ "tag": "plugin/sv", "schema": %s, "defaults": %s }""".formatted(PLUGIN_SCHEMA, PLUGIN_DEFAULTS))
			.andExpect(status().isCreated());
	}

	void createTemplate() throws Exception {
		postJson("/api/v1/template", """
			{ "tag": "sv", "schema": %s, "defaults": %s }""".formatted(TEMPLATE_SCHEMA, TEMPLATE_DEFAULTS))
			.andExpect(status().isCreated());
	}

	ResultActions postRef(String url, String plugin) throws Exception {
		return postJson("/api/v1/ref", """
			{ "url": "%s", "tags": ["public", "plugin/sv"]%s }"""
			.formatted(url, plugin == null ? "" : ", \"plugins\": { \"plugin/sv\": " + plugin + " }"));
	}

	JsonNode storedPlugin(String url) throws Exception {
		return getJson(get("/api/v1/ref").param("url", url)).get("plugins").get("plugin/sv");
	}

	ResultActions expectPluginError(ResultActions result, String tag, String detail) throws Exception {
		return result
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value(ErrorConstants.PLUGIN_VALIDATION_TYPE.toString()))
			.andExpect(jsonPath("$.title").value("Bad Request"))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.instance").value("/api/v1/ref"))
			.andExpect(jsonPath("$.path").value("/api/v1/ref"))
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
			.andExpect(jsonPath("$.tag").value(tag))
			.andExpect(jsonPath("$.reason").value("schema"))
			.andExpect(jsonPath("$.detail").value(detail));
	}

	ResultActions expectTemplateError(ResultActions result, String tag, String detail) throws Exception {
		return result
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value(ErrorConstants.TEMPLATE_VALIDATION_TYPE.toString()))
			.andExpect(jsonPath("$.title").value("Bad Request"))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.instance").value("/api/v1/ext"))
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_TEMPLATE))
			.andExpect(jsonPath("$.tag").value(tag))
			.andExpect(jsonPath("$.reason").value("schema"))
			.andExpect(jsonPath("$.detail").value(detail));
	}

	/**
	 * Errors follow the property order of the stored schema. PostgreSQL jsonb reorders object keys
	 * (shorter keys first), SQLite keeps them, so the order of errors differs by database.
	 * The detail always summarizes the first {@link ValidationErrors#SUMMARY_ERRORS} errors in response order.
	 */
	ResultActions expectUnorderedPluginError(ResultActions result, String tag, String suffix) throws Exception {
		result
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value(ErrorConstants.PLUGIN_VALIDATION_TYPE.toString()))
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
			.andExpect(jsonPath("$.tag").value(tag))
			.andExpect(jsonPath("$.reason").value("schema"));
		var body = mapper.readTree(result.andReturn().getResponse().getContentAsString());
		var messages = new ArrayList<String>();
		body.get("errors").forEach(e -> messages.add(e.get("message").asText()));
		assertThat(body.get("detail").asText()).isEqualTo(tag + ": " + String.join("; ", messages.subList(0, Math.min(messages.size(), ValidationErrors.SUMMARY_ERRORS))) + suffix);
		return result;
	}

	JsonNode errors(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString()).get("errors");
	}

	// Ref plugins

	@Test
	void testRefPluginMatchingSchema() throws Exception {
		createPlugin();

		postRef("https://www.example.com/1", """
			{ "name": "Bob", "age": 30, "labels": ["a", "b"] }""")
			.andExpect(status().isCreated());

		// Defaults are not merged into valid data on create
		assertThat(storedPlugin("https://www.example.com/1")).isEqualTo(json("""
			{ "name": "Bob", "age": 30, "labels": ["a", "b"] }"""));
	}

	@Test
	void testRefPluginIntegralFloatMatchesUint32() throws Exception {
		createPlugin();

		postRef("https://www.example.com/1", """
			{ "name": "Bob", "age": 1.0 }""")
			.andExpect(status().isCreated());

		assertThat(storedPlugin("https://www.example.com/1")).isEqualTo(json("""
			{ "name": "Bob", "age": 1.0 }"""));
	}

	@Test
	void testRefPluginDefaultsWithoutData() throws Exception {
		createPlugin();

		postRef("https://www.example.com/1", null)
			.andExpect(status().isCreated());

		assertThat(storedPlugin("https://www.example.com/1")).isEqualTo(json(PLUGIN_DEFAULTS));
	}

	@Test
	void testRefPluginDefaultsWithNullData() throws Exception {
		createPlugin();

		postRef("https://www.example.com/1", "null")
			.andExpect(status().isCreated());

		assertThat(storedPlugin("https://www.example.com/1")).isEqualTo(json(PLUGIN_DEFAULTS));
	}

	@Test
	void testRefPluginEmptyDataIsNotDefaulted() throws Exception {
		createPlugin();

		var result = expectUnorderedPluginError(postRef("https://www.example.com/1", "{}"), "plugin/sv", "")
			.andExpect(jsonPath("$.truncated").value(false))
			.andExpect(jsonPath("$.errors", hasSize(2)));

		assertThat(errors(result)).containsExactlyInAnyOrderElementsOf(json("""
			[
				{ "path": "/name", "schemaPath": "/properties/name", "code": "missing", "expected": "string", "message": "name: required" },
				{ "path": "/age", "schemaPath": "/properties/age", "code": "missing", "expected": "uint32", "message": "age: required" }
			]"""));
		assertThat(refRepository.count()).isZero();
	}

	@Test
	void testRefPluginPartialDataIsNotDefaulted() throws Exception {
		createPlugin();

		var result = expectPluginError(postRef("https://www.example.com/1", """
			{ "name": "Bob" }"""), "plugin/sv", "plugin/sv: age: required")
			.andExpect(jsonPath("$.truncated").value(false));

		assertThat(errors(result)).isEqualTo(json("""
			[{ "path": "/age", "schemaPath": "/properties/age", "code": "missing", "expected": "uint32", "message": "age: required" }]"""));
		assertThat(refRepository.count()).isZero();
	}

	@Test
	void testRefPluginWrongType() throws Exception {
		createPlugin();

		for (var age : new String[]{ "\"old\"", "-1", "1.5", "true", "4294967296" }) {
			var result = expectPluginError(postRef("https://www.example.com/1", """
				{ "name": "Bob", "age": %s }""".formatted(age)), "plugin/sv", "plugin/sv: age: expected uint32")
				.andExpect(jsonPath("$.truncated").value(false));

			assertThat(errors(result)).isEqualTo(json("""
				[{ "path": "/age", "schemaPath": "/properties/age/type", "code": "type", "expected": "uint32", "message": "age: expected uint32" }]"""));
		}
		assertThat(refRepository.count()).isZero();
	}

	@Test
	void testRefPluginNullProperty() throws Exception {
		createPlugin();

		var result = expectPluginError(postRef("https://www.example.com/1", """
			{ "name": null, "age": 1 }"""), "plugin/sv", "plugin/sv: name: must not be null");

		assertThat(errors(result)).isEqualTo(json("""
			[{ "path": "/name", "schemaPath": "/properties/name/type", "code": "nullable", "expected": "string", "message": "name: must not be null" }]"""));
	}

	@Test
	void testRefPluginAdditionalProperty() throws Exception {
		createPlugin();

		var result = expectPluginError(postRef("https://www.example.com/1", """
			{ "name": "Bob", "age": 1, "foo": 1 }"""), "plugin/sv", "plugin/sv: foo: not allowed");

		// Additional properties are reported against the root object schema
		assertThat(errors(result)).isEqualTo(json("""
			[{ "path": "/foo", "schemaPath": "", "code": "unexpected", "expected": null, "message": "foo: not allowed" }]"""));
		assertThat(refRepository.count()).isZero();
	}

	@Test
	void testRefPluginManyKindsOfErrors() throws Exception {
		createPlugin();

		var result = expectUnorderedPluginError(postRef("https://www.example.com/1", """
			{ "age": true, "labels": [1, "a", 2], "extra": "no", "foo": 1 }"""), "plugin/sv", "; and 1 more")
			.andExpect(jsonPath("$.truncated").value(false));

		assertThat(errors(result)).containsExactlyInAnyOrderElementsOf(json("""
			[
				{ "path": "/name", "schemaPath": "/properties/name", "code": "missing", "expected": "string", "message": "name: required" },
				{ "path": "/age", "schemaPath": "/properties/age/type", "code": "type", "expected": "uint32", "message": "age: expected uint32" },
				{ "path": "/labels/0", "schemaPath": "/optionalProperties/labels/elements/type", "code": "type", "expected": "string", "message": "labels/0: expected string" },
				{ "path": "/labels/2", "schemaPath": "/optionalProperties/labels/elements/type", "code": "type", "expected": "string", "message": "labels/2: expected string" },
				{ "path": "/extra", "schemaPath": "/optionalProperties/extra/type", "code": "type", "expected": "boolean", "message": "extra: expected boolean" },
				{ "path": "/foo", "schemaPath": "", "code": "unexpected", "expected": null, "message": "foo: not allowed" }
			]"""));
	}

	// Truncation

	ResultActions postManyErrors(int count) throws Exception {
		var list = IntStream.range(0, count).mapToObj(i -> "\"x\"").collect(joining(","));
		return postJson("/api/v1/ref", """
			{ "url": "https://www.example.com/%d", "tags": ["public", "plugin/many"], "plugins": { "plugin/many": { "list": [%s] } } }"""
			.formatted(count, list));
	}

	void assertManyErrors(ResultActions result, int count) throws Exception {
		var errors = errors(result);
		assertThat(errors).hasSize(count);
		for (var i = 0; i < count; i++) {
			assertThat(errors.get(i)).isEqualTo(json("""
				{ "path": "/list/%d", "schemaPath": "/properties/list/elements/type", "code": "type", "expected": "uint32", "message": "list/%d: expected uint32" }"""
				.formatted(i, i)));
		}
	}

	@Test
	void testTruncatedErrors() throws Exception {
		postJson("/api/v1/plugin", """
			{ "tag": "plugin/many", "schema": { "properties": { "list": { "elements": { "type": "uint32" } } } } }""")
			.andExpect(status().isCreated());
		var first5 = "plugin/many: list/0: expected uint32; list/1: expected uint32; list/2: expected uint32; list/3: expected uint32; list/4: expected uint32";
		assertThat(ValidationErrors.MAX_ERRORS).isEqualTo(50);
		assertThat(ValidationErrors.SUMMARY_ERRORS).isEqualTo(5);

		var r5 = expectPluginError(postManyErrors(5), "plugin/many", first5)
			.andExpect(jsonPath("$.truncated").value(false));
		assertManyErrors(r5, 5);

		var r6 = expectPluginError(postManyErrors(6), "plugin/many", first5 + "; and 1 more")
			.andExpect(jsonPath("$.truncated").value(false));
		assertManyErrors(r6, 6);

		var r50 = expectPluginError(postManyErrors(50), "plugin/many", first5 + "; and 45 more")
			.andExpect(jsonPath("$.truncated").value(false));
		assertManyErrors(r50, 50);

		var r51 = expectPluginError(postManyErrors(51), "plugin/many", first5 + "; and at least 46 more")
			.andExpect(jsonPath("$.truncated").value(true));
		assertManyErrors(r51, 50);

		// The validator stops at MAX_ERRORS + 1, so the remaining count is a lower bound
		var r60 = expectPluginError(postManyErrors(60), "plugin/many", first5 + "; and at least 46 more")
			.andExpect(jsonPath("$.truncated").value(true));
		assertManyErrors(r60, 50);

		assertThat(refRepository.count()).isZero();
	}

	// Ext config

	@Test
	void testExtConfigMatchingSchema() throws Exception {
		createTemplate();

		postJson("/api/v1/ext", """
			{ "tag": "sv", "config": { "title": "T", "size": 4 } }""")
			.andExpect(status().isCreated());

		assertThat(getJson(get("/api/v1/ext").param("tag", "sv")).get("config")).isEqualTo(json("""
			{ "title": "T", "size": 4 }"""));
	}

	@Test
	void testExtConfigDefaultsWithoutConfig() throws Exception {
		createTemplate();

		postJson("/api/v1/ext", """
			{ "tag": "sv/child" }""")
			.andExpect(status().isCreated());

		assertThat(getJson(get("/api/v1/ext").param("tag", "sv/child")).get("config")).isEqualTo(json(TEMPLATE_DEFAULTS));
	}

	@Test
	void testExtConfigPartialIsNotDefaulted() throws Exception {
		createTemplate();

		var result = expectTemplateError(postJson("/api/v1/ext", """
			{ "tag": "sv", "config": { "size": 4 } }"""), "sv", "sv: title: required")
			.andExpect(jsonPath("$.truncated").value(false));

		assertThat(errors(result)).isEqualTo(json("""
			[{ "path": "/title", "schemaPath": "/properties/title", "code": "missing", "expected": "string", "message": "title: required" }]"""));
		assertThat(extRepository.count()).isZero();
	}

	@Test
	void testExtConfigWrongType() throws Exception {
		createTemplate();

		var result = expectTemplateError(postJson("/api/v1/ext", """
			{ "tag": "sv", "config": { "title": "T", "size": 400 } }"""), "sv", "sv: size: expected int8")
			.andExpect(jsonPath("$.truncated").value(false));

		assertThat(errors(result)).isEqualTo(json("""
			[{ "path": "/size", "schemaPath": "/optionalProperties/size/type", "code": "type", "expected": "int8", "message": "size: expected int8" }]"""));
		assertThat(extRepository.count()).isZero();
	}

	@Test
	void testExtConfigAdditionalProperty() throws Exception {
		createTemplate();

		var result = expectTemplateError(postJson("/api/v1/ext", """
			{ "tag": "sv", "config": { "title": "T", "foo": 1 } }"""), "sv", "sv: foo: not allowed")
			.andExpect(jsonPath("$.truncated").value(false));

		assertThat(errors(result)).isEqualTo(json("""
			[{ "path": "/foo", "schemaPath": "", "code": "unexpected", "expected": null, "message": "foo: not allowed" }]"""));
		assertThat(extRepository.count()).isZero();
	}

	// Invalid schemas

	@Test
	void testPluginSchemaFailingVerify() throws Exception {
		for (var schema : new String[]{ """
			{ "type": "string", "elements": {} }""", """
			{ "ref": "missing" }""" }) {
			postJson("/api/v1/plugin", """
				{ "tag": "plugin/bad", "schema": %s }""".formatted(schema))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(ErrorConstants.CONSTRAINT_VIOLATION_TYPE.toString()))
				.andExpect(jsonPath("$.title").value("Method argument not valid"))
				.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_VALIDATION))
				// The message template is not interpolated
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].objectName").value("plugin"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("schema"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("{schema.invalid}"));
		}
		assertThat(pluginRepository.count()).isZero();
	}

	@Test
	void testTemplateSchemaFailingVerify() throws Exception {
		postJson("/api/v1/template", """
			{ "tag": "bad", "schema": { "ref": "missing" } }""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type").value(ErrorConstants.CONSTRAINT_VIOLATION_TYPE.toString()))
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_VALIDATION))
			.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
			.andExpect(jsonPath("$.fieldErrors[0].objectName").value("template"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("schema"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("{schema.invalid}"));
		assertThat(templateRepository.count()).isZero();
	}

	/**
	 * Schemas that can't be bound to a JTD {@code Schema} fail inside the constraint validator.
	 * Suspected bug: these are 500 Internal Server Error instead of 400.
	 */
	@Test
	void testUnbindableSchemaIsServerError() throws Exception {
		for (var api : new String[]{ "plugin", "template" }) {
			var tag = api.equals("plugin") ? "plugin/bad" : "bad";
			postJson("/api/v1/" + api, """
				{ "tag": "%s", "schema": { "type": "notatype" } }""".formatted(tag))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(ErrorConstants.DEFAULT_TYPE.toString()))
				.andExpect(jsonPath("$.title").value("Internal Server Error"))
				.andExpect(jsonPath("$.message").value("error.http.500"))
				.andExpect(jsonPath("$.detail").value(startsWith(
					"Cannot deserialize value of type `com.jsontypedef.jtd.Type` from String \"notatype\"")))
				.andExpect(jsonPath("$.detail").value(endsWith(
					"(through reference chain: com.jsontypedef.jtd.Schema[\"type\"])")));
			postJson("/api/v1/" + api, """
				{ "tag": "%s", "schema": { "properties": "x" } }""".formatted(tag))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value(endsWith(
					"(through reference chain: com.jsontypedef.jtd.Schema[\"properties\"])")));
			postJson("/api/v1/" + api, """
				{ "tag": "%s", "schema": { "foo": 1 } }""".formatted(tag))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value(startsWith(
					"Unrecognized field \"foo\" (class com.jsontypedef.jtd.Schema), not marked as ignorable")));
		}
		assertThat(pluginRepository.count()).isZero();
		assertThat(templateRepository.count()).isZero();
	}

	/**
	 * Suspected bug: PATCH skips {@code @SchemaValid}, so a malformed schema is stored
	 * and every later Ref write with the tag fails with 500.
	 */
	@Test
	void testPatchAcceptsInvalidPluginSchema() throws Exception {
		postJson("/api/v1/plugin", """
			{ "tag": "plugin/sv", "schema": { "type": "string" } }""")
			.andExpect(status().isCreated());
		var cursor = pluginRepository.findOneByQualifiedTag("plugin/sv").orElseThrow().getModified();

		mockMvc
			.perform(admin(patch("/api/v1/plugin"))
				.param("tag", "plugin/sv")
				.param("cursor", cursor.toString())
				.contentType("application/merge-patch+json")
				.content("""
					{ "schema": { "type": "notatype" } }"""))
			.andExpect(status().isOk());
		configCache.clearPluginCache();

		assertThat(pluginRepository.findOneByQualifiedTag("plugin/sv").orElseThrow().getSchema()).isEqualTo(json("""
			{ "type": "notatype" }"""));
		postRef("https://www.example.com/1", "\"hi\"")
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.detail").value(startsWith(
				"Cannot deserialize value of type `com.jsontypedef.jtd.Type` from String \"notatype\"")));
		assertThat(refRepository.count()).isZero();
	}

	/**
	 * Merge-patching an unrelated field of a Plugin without defaults stores a JSON null
	 * {@code defaults}, which is then rendered as {@code "defaults": null} (a NullNode is not NON_EMPTY).
	 */
	@Test
	void testPatchPluginWithoutDefaultsRendersNullDefaults() throws Exception {
		postJson("/api/v1/plugin", """
			{ "tag": "plugin/sv", "schema": { "type": "string" } }""")
			.andExpect(status().isCreated());
		var before = getJson(get("/api/v1/plugin").param("tag", "plugin/sv"));
		assertThat(before.has("defaults")).isFalse();
		assertThat(before.has("config")).isFalse();
		assertThat(pluginRepository.findOneByQualifiedTag("plugin/sv").orElseThrow().getDefaults()).isNull();

		mockMvc
			.perform(admin(patch("/api/v1/plugin"))
				.param("tag", "plugin/sv")
				.param("cursor", before.get("modified").asText())
				.contentType("application/merge-patch+json")
				.content("""
					{ "name": "Named" }"""))
			.andExpect(status().isOk());

		var after = getJson(get("/api/v1/plugin").param("tag", "plugin/sv"));
		assertThat(after.get("name").asText()).isEqualTo("Named");
		assertThat(after.has("config")).isFalse();
		assertThat(after.has("defaults")).isTrue();
		assertThat(after.get("defaults").isNull()).isTrue();
		assertThat(pluginRepository.findOneByQualifiedTag("plugin/sv").orElseThrow().getDefaults().isNull()).isTrue();
	}

	/**
	 * Patching a Ref merges the plugin defaults into its stored plugin data first,
	 * so an unrelated patch fills in missing optional properties.
	 */
	@Test
	void testRefPatchMergesPluginDefaults() throws Exception {
		createPlugin();
		postRef("https://www.example.com/1", """
			{ "name": "Bob", "age": 2 }""")
			.andExpect(status().isCreated());
		var before = getJson(get("/api/v1/ref").param("url", "https://www.example.com/1"));
		assertThat(before.get("plugins").get("plugin/sv")).isEqualTo(json("""
			{ "name": "Bob", "age": 2 }"""));

		mockMvc
			.perform(admin(patch("/api/v1/ref"))
				.param("url", "https://www.example.com/1")
				.param("cursor", before.get("modified").asText())
				.contentType("application/merge-patch+json")
				.content("""
					{ "title": "Patched" }"""))
			.andExpect(status().isOk());

		var after = getJson(get("/api/v1/ref").param("url", "https://www.example.com/1"));
		assertThat(after.get("title").asText()).isEqualTo("Patched");
		assertThat(after.get("plugins").get("plugin/sv")).isEqualTo(json("""
			{ "name": "Bob", "age": 2, "extra": true }"""));
	}
}
