package jasper.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Template;
import jasper.repository.ExtRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Structured validation errors for Ext config and Template config.
 */
@AutoConfigureMockMvc
@IntegrationTest
class TemplateValidationIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	ConfigCache configCache;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	Props props;

	@BeforeEach
	void init() {
		extRepository.deleteAll();
		templateRepository.deleteAll();
		configCache.clearTemplateCache();
		configCache.clearConfigCache();
		// The default user is used for requests, so set the admin user by header
		props.setAllowUserTagHeader(true);
		props.setAllowUserRoleHeader(true);
	}

	@AfterEach
	void cleanup() {
		props.setAllowUserTagHeader(false);
		props.setAllowUserRoleHeader(false);
		templateRepository.deleteAll();
		configCache.clearTemplateCache();
		configCache.clearConfigCache();
	}

	ObjectNode json(String json) {
		try {
			return (ObjectNode) mapper.readTree(json);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	void createTemplate(String tag, String schema, String defaults) {
		var template = new Template();
		template.setTag(tag);
		if (schema != null) template.setSchema(json(schema));
		if (defaults != null) template.setDefaults(json(defaults));
		templateRepository.save(template);
		configCache.clearTemplateCache();
	}

	ResultActions postAsAdmin(String api, Object body) throws Exception {
		return mockMvc
			.perform(post(api)
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsBytes(body))
				.header("User-Tag", "+user/tester")
				.header("User-Role", "ROLE_ADMIN")
				.with(csrf().asHeader()));
	}

	ResultActions postExt(String tag, String config) throws Exception {
		var ext = new Ext();
		ext.setTag(tag);
		if (config != null) ext.setConfig(json(config));
		return postAsAdmin("/api/v1/ext", ext);
	}

	@Test
	void testExtConfigSchemaErrors() throws Exception {
		createTemplate("test", """
			{
				"properties": { "age": { "type": "uint32" } },
				"optionalProperties": { "kind": { "enum": ["a", "b"] } }
			}""", null);

		var body = postExt("test", """
			{ "kind": "secret-value", "extra": 1 }""")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_TEMPLATE))
			.andExpect(jsonPath("$.type").value(ErrorConstants.TEMPLATE_VALIDATION_TYPE.toString()))
			.andExpect(jsonPath("$.tag").value("test"))
			.andExpect(jsonPath("$.reason").value("schema"))
			.andExpect(jsonPath("$.truncated").value(false))
			.andExpect(jsonPath("$.errors[*].path").value(containsInAnyOrder("/age", "/kind", "/extra")))
			.andExpect(jsonPath("$.errors[*].code").value(containsInAnyOrder("missing", "enum", "unexpected")))
			.andExpect(jsonPath("$.errors[?(@.code == 'enum')].expected").value(containsInAnyOrder("a, b")))
			.andExpect(jsonPath("$.errors[?(@.code == 'missing')].expected").value(containsInAnyOrder("uint32")))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("secret-value");
	}

	@Test
	void testExtConfigWrongTypeNested() throws Exception {
		createTemplate("test", """
			{ "properties": { "a": { "properties": { "b": { "elements": { "type": "uint32" } } } } } }""", null);

		postExt("test", """
			{ "a": { "b": ["x"] } }""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.reason").value("schema"))
			.andExpect(jsonPath("$.detail").value("test: a/b/0: expected uint32"))
			.andExpect(jsonPath("$.errors[0].path").value("/a/b/0"))
			.andExpect(jsonPath("$.errors[0].schemaPath").value("/properties/a/properties/b/elements/type"))
			.andExpect(jsonPath("$.errors[0].code").value("type"))
			.andExpect(jsonPath("$.errors[0].expected").value("uint32"));
	}

	@Test
	void testExtConfigWithoutTemplate() throws Exception {
		postExt("plain", """
			{ "name": "John" }""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_TEMPLATE))
			.andExpect(jsonPath("$.tag").value("plain"))
			.andExpect(jsonPath("$.reason").value("schemaless"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void testExtConfigExceedingMaxDepth() throws Exception {
		createTemplate("test", """
			{
				"definitions": { "node": { "optionalProperties": { "next": { "ref": "node" } } } },
				"ref": "node"
			}""", null);
		var config = mapper.createObjectNode();
		var node = config;
		for (var i = 0; i < 40; i++) node = node.putObject("next");

		postExt("test", config.toString())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_TEMPLATE))
			.andExpect(jsonPath("$.reason").value("maxDepth"));
	}

	@Test
	void testExtWithInvalidTemplateDefaults() throws Exception {
		createTemplate("test", """
			{ "properties": { "age": { "type": "uint32" } } }""", """
			{ "age": "zero" }""");

		postExt("test", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_TEMPLATE))
			.andExpect(jsonPath("$.tag").value("test"))
			.andExpect(jsonPath("$.reason").value("invalidDefaults"))
			.andExpect(jsonPath("$.errors[0].path").value("/age"))
			.andExpect(jsonPath("$.errors[0].code").value("type"));
	}

	@Test
	void testServerConfigBadField() throws Exception {
		var template = new Template();
		template.setTag("_config/server");
		template.setConfig(json("""
			{ "maxSources": "secret-value" }"""));

		var body = postAsAdmin("/api/v1/template", template)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_TEMPLATE))
			.andExpect(jsonPath("$.tag").value("_config/server"))
			.andExpect(jsonPath("$.reason").value("config"))
			.andExpect(jsonPath("$.errors[0].path").value("/maxSources"))
			.andExpect(jsonPath("$.errors[0].code").value("type"))
			.andExpect(jsonPath("$.errors[0].expected").value("int"))
			.andExpect(jsonPath("$.detail").value("_config/server: maxSources: expected int"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("secret-value");
	}
}
