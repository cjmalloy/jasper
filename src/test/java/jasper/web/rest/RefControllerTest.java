package jasper.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.web.rest.errors.ErrorConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link RefController} with plugin schema validation.
 */
@WithMockUser("+user/tester")
@AutoConfigureMockMvc
@IntegrationTest
class RefControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefRepository refRepository;

    @Autowired
    private PluginRepository pluginRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ConfigCache configCache;

    @Autowired
    private Props props;

    static final String URL = "https://www.example.com/test";

    Plugin createPluginWithSchema(String tag) {
        var plugin = new Plugin();
        plugin.setTag(tag);
        try {
            plugin.setSchema((ObjectNode) mapper.readTree("""
            {
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "uint32" }
                },
                "optionalProperties": {
                    "email": { "type": "string" }
                }
            }"""));
            plugin.setDefaults((ObjectNode) mapper.readTree("""
            {
                "name": "default",
                "age": 0
            }"""));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return plugin;
    }

    @BeforeEach
    void init() {
        refRepository.deleteAll();
        pluginRepository.deleteAll();
        configCache.clearPluginCache();
    }

    @AfterEach
    void cleanup() {
        props.setAllowUserTagHeader(false);
    }

    Plugin createPlugin(String tag, String schema, String defaults) {
        var plugin = new Plugin();
        plugin.setTag(tag);
        try {
            if (schema != null) plugin.setSchema((ObjectNode) mapper.readTree(schema));
            if (defaults != null) plugin.setDefaults((ObjectNode) mapper.readTree(defaults));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return pluginRepository.save(plugin);
    }

    Ref createRef(String url, String tag, String pluginData) {
        var ref = new Ref();
        ref.setUrl(url);
        ref.setTags(new ArrayList<>(List.of("public", tag)));
        if (pluginData != null) {
            try {
                ref.setPlugin(tag, mapper.readTree(pluginData));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return ref;
    }

    ResultActions postRef(Ref ref) throws Exception {
        return mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()));
    }

    @Test
    void testCreateRefWithValidPluginData() throws Exception {
        // Create a plugin with schema
        var plugin = createPluginWithSchema("plugin/test");
        pluginRepository.save(plugin);

        // Create a ref with valid plugin data
        var ref = new Ref();
        ref.setUrl(URL);
        ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
        var pluginData = mapper.createObjectNode();
        pluginData.put("name", "John");
        pluginData.put("age", 30);
        ref.setPlugin("plugin/test", pluginData);

        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isCreated());
    }

    @Test
    void testCreateRefWithMissingRequiredPluginField() throws Exception {
        // Create a plugin with schema requiring "name" and "age"
        var plugin = createPluginWithSchema("plugin/test");
        pluginRepository.save(plugin);

        // Create a ref with missing required field "age"
        var ref = new Ref();
        ref.setUrl(URL + "/missing-field");
        ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
        var pluginData = mapper.createObjectNode();
        pluginData.put("name", "John");
        // "age" field is missing
        ref.setPlugin("plugin/test", pluginData);

        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.tag").value("plugin/test"))
            .andExpect(jsonPath("$.reason").value("schema"))
            .andExpect(jsonPath("$.detail").value("plugin/test: age: required"))
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].path").value("/age"))
            .andExpect(jsonPath("$.errors[0].schemaPath").value("/properties/age"))
            .andExpect(jsonPath("$.errors[0].code").value("missing"))
            .andExpect(jsonPath("$.errors[0].expected").value("uint32"))
            .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void testCreateRefWithExtraPluginField() throws Exception {
        // Create a plugin with schema that only allows "name" and "age" properties
        var plugin = new Plugin();
        plugin.setTag("plugin/strict");
        try {
            // Schema without additionalProperties - strict validation
            plugin.setSchema((ObjectNode) mapper.readTree("""
            {
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "uint32" }
                }
            }"""));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        pluginRepository.save(plugin);

        // Create a ref with an extra field "extra" not in schema
        var ref = new Ref();
        ref.setUrl(URL + "/extra-field");
        ref.setTags(new ArrayList<>(List.of("public", "plugin/strict")));
        var pluginData = mapper.createObjectNode();
        pluginData.put("name", "John");
        pluginData.put("age", 30);
        pluginData.put("extra", "not allowed");  // This field is not in schema
        ref.setPlugin("plugin/strict", pluginData);

        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.tag").value("plugin/strict"))
            .andExpect(jsonPath("$.reason").value("schema"))
            .andExpect(jsonPath("$.detail").value("plugin/strict: extra: not allowed"))
            .andExpect(jsonPath("$.errors[0].path").value("/extra"))
            .andExpect(jsonPath("$.errors[0].schemaPath").value(""))
            .andExpect(jsonPath("$.errors[0].code").value("unexpected"));
    }

    @Test
    void testCreateRefWithWrongTypePluginField() throws Exception {
        // Create a plugin with schema
        var plugin = createPluginWithSchema("plugin/test");
        pluginRepository.save(plugin);

        // Create a ref with wrong type for "age" field (string instead of uint32)
        var ref = new Ref();
        ref.setUrl(URL + "/wrong-type");
        ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
        var pluginData = mapper.createObjectNode();
        pluginData.put("name", "John");
        pluginData.put("age", "thirty");  // Wrong type: string instead of uint32
        ref.setPlugin("plugin/test", pluginData);

        var body = mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.detail").value("plugin/test: age: expected uint32"))
            .andExpect(jsonPath("$.errors[0].path").value("/age"))
            .andExpect(jsonPath("$.errors[0].schemaPath").value("/properties/age/type"))
            .andExpect(jsonPath("$.errors[0].code").value("type"))
            .andExpect(jsonPath("$.errors[0].expected").value("uint32"))
            .andExpect(jsonPath("$.errors[0].message").value("age: expected uint32"))
            .andReturn().getResponse().getContentAsString();
        // The submitted value is never echoed back
        assertThat(body).doesNotContain("thirty");
    }

    @Test
    void testUpdateRefWithUnwritablePluginTag() throws Exception {
        // Create a plugin with schema
        var plugin = createPluginWithSchema("plugin/test");
        pluginRepository.save(plugin);

        // First create a valid ref
        var ref = new Ref();
        ref.setUrl(URL + "/update-test");
        ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
        var validPluginData = mapper.createObjectNode();
        validPluginData.put("name", "John");
        validPluginData.put("age", 30);
        ref.setPlugin("plugin/test", validPluginData);

        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isCreated());

        // Now try to update - the user doesn't have write access to plugin/test tag after creation
        // So this fails with 403 Forbidden due to access control, not 400 Bad Request due to validation
        var updatedPluginData = mapper.createObjectNode();
        updatedPluginData.put("name", "Jane");
        updatedPluginData.put("age", -5);  // This would be invalid, but access control check happens first
        ref.setPlugin("plugin/test", updatedPluginData);

        mockMvc
            .perform(put("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_ACCESS_DENIED));
    }

    @Test
    void testCreateRefWithOptionalPluginField() throws Exception {
        // Create a plugin with schema that has optional fields
        var plugin = createPluginWithSchema("plugin/test");
        pluginRepository.save(plugin);

        // Create a ref with optional field "email"
        var ref = new Ref();
        ref.setUrl(URL + "/optional-field");
        ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
        var pluginData = mapper.createObjectNode();
        pluginData.put("name", "John");
        pluginData.put("age", 30);
        pluginData.put("email", "john@example.com");  // Optional field
        ref.setPlugin("plugin/test", pluginData);

        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isCreated());
    }

    @Test
    void testCreateRefWithInvalidPluginDataParsesError() throws Exception {
        // Create a plugin with schema
        var plugin = createPluginWithSchema("plugin/test");
        pluginRepository.save(plugin);

        // Create a ref with multiple validation errors
        var ref = new Ref();
        ref.setUrl(URL + "/multiple-errors");
        ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
        var pluginData = mapper.createObjectNode();
        // Missing "name" field
        pluginData.put("age", "not_a_number");  // Wrong type
        ref.setPlugin("plugin/test", pluginData);

        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.detail").value(containsString("plugin/test")))
            .andExpect(jsonPath("$.errors[*].path").value(containsInAnyOrder("/name", "/age")))
            .andExpect(jsonPath("$.errors[*].code").value(containsInAnyOrder("missing", "type")))
            .andExpect(jsonPath("$.errors[?(@.code == 'type')].expected").value(containsInAnyOrder("uint32")))
            .andExpect(jsonPath("$.detail").value(not(containsString("not_a_number"))));
    }

    @Test
    void testCreateRefWithPluginDataButNoTag() throws Exception {
        // Create a plugin with schema
        var plugin = createPluginWithSchema("plugin/test");
        pluginRepository.save(plugin);

        // Create a ref with plugin data but without the corresponding tag
        var ref = new Ref();
        ref.setUrl(URL + "/no-tag");
        ref.setTags(new ArrayList<>(List.of("public")));
        var pluginData = mapper.createObjectNode();
        pluginData.put("name", "John");
        pluginData.put("age", 30);
        // Set plugin data directly, since setPlugin would also add the tag
        var plugins = mapper.createObjectNode();
        plugins.set("plugin/test", pluginData);
        ref.setPlugins(plugins);

        // Plugin data must be tagged
        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.type").value(ErrorConstants.PLUGIN_VALIDATION_TYPE.toString()))
            .andExpect(jsonPath("$.tag").value("plugin/test"))
            .andExpect(jsonPath("$.reason").value("untagged"))
            .andExpect(jsonPath("$.errors").isEmpty())
            .andExpect(jsonPath("$.detail").value(containsString("plugin/test")));
    }

    @Test
    void testCreateRefWithInvalidEnumPluginField() throws Exception {
        createPlugin("plugin/enum", """
            { "properties": { "kind": { "enum": ["b", "a"] } } }""", null);

        postRef(createRef(URL + "/enum", "plugin/enum", """
            { "kind": "c" }"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.reason").value("schema"))
            .andExpect(jsonPath("$.errors[0].path").value("/kind"))
            .andExpect(jsonPath("$.errors[0].schemaPath").value("/properties/kind/enum"))
            .andExpect(jsonPath("$.errors[0].code").value("enum"))
            .andExpect(jsonPath("$.errors[0].expected").value("a, b"))
            .andExpect(jsonPath("$.errors[0].message").value("kind: expected one of a, b"));
    }

    @Test
    void testCreateRefWithNestedPluginError() throws Exception {
        createPlugin("plugin/nested", """
            { "properties": { "a": { "properties": { "b": { "elements": { "type": "string" } } } } } }""", null);

        postRef(createRef(URL + "/nested", "plugin/nested", """
            { "a": { "b": [1] } }"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("schema"))
            .andExpect(jsonPath("$.errors[0].path").value("/a/b/0"))
            .andExpect(jsonPath("$.errors[0].schemaPath").value("/properties/a/properties/b/elements/type"))
            .andExpect(jsonPath("$.errors[0].code").value("type"))
            .andExpect(jsonPath("$.errors[0].expected").value("string"))
            .andExpect(jsonPath("$.detail").value("plugin/nested: a/b/0: expected string"));
    }

    @Test
    void testCreateRefWithNullPluginField() throws Exception {
        pluginRepository.save(createPluginWithSchema("plugin/test"));

        postRef(createRef(URL + "/null", "plugin/test", """
            { "name": null, "age": 1 }"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].path").value("/name"))
            .andExpect(jsonPath("$.errors[0].code").value("nullable"))
            .andExpect(jsonPath("$.errors[0].expected").value("string"));
    }

    @Test
    void testCreateRefWithManyPluginErrorsIsTruncated() throws Exception {
        var schema = mapper.createObjectNode();
        var props = schema.putObject("properties");
        var data = mapper.createObjectNode();
        for (var i = 0; i < 60; i++) {
            props.putObject("f" + i).put("type", "string");
            data.put("f" + i, i);
        }
        createPlugin("plugin/many", schema.toString(), null);

        postRef(createRef(URL + "/many", "plugin/many", data.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(50))
            .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void testCreateRefWithSchemalessPluginData() throws Exception {
        createPlugin("plugin/schemaless", null, null);

        postRef(createRef(URL + "/schemaless", "plugin/schemaless", """
            { "name": "John" }"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.tag").value("plugin/schemaless"))
            .andExpect(jsonPath("$.reason").value("schemaless"))
            .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void testCreateRefWithPluginDataExceedingMaxDepth() throws Exception {
        createPlugin("plugin/deep", """
            {
                "definitions": { "node": { "optionalProperties": { "next": { "ref": "node" } } } },
                "ref": "node"
            }""", null);
        var data = mapper.createObjectNode();
        var node = data;
        for (var i = 0; i < 40; i++) node = node.putObject("next");

        postRef(createRef(URL + "/deep", "plugin/deep", data.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.tag").value("plugin/deep"))
            .andExpect(jsonPath("$.reason").value("maxDepth"));
    }

    @Test
    void testCreateRefWithInvalidPluginDefaults() throws Exception {
        createPlugin("plugin/defaults", """
            { "properties": { "age": { "type": "uint32" } } }""", """
            { "age": "zero" }""");

        postRef(createRef(URL + "/defaults", "plugin/defaults", null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.tag").value("plugin/defaults"))
            .andExpect(jsonPath("$.reason").value("invalidDefaults"))
            .andExpect(jsonPath("$.errors[0].path").value("/age"))
            .andExpect(jsonPath("$.errors[0].code").value("type"));
    }

    ResultActions postRefAsUser(Ref ref) throws Exception {
        // The mock user is replaced by the default user, so set the user tag by header
        props.setAllowUserTagHeader(true);
        return mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .header("User-Tag", "+user/tester")
                .with(csrf().asHeader()));
    }

    @Test
    void testCreateUserUrlWithoutSource() throws Exception {
        var ref = createRef("tag:/+user/tester?url=" + URL, "plugin/user", null);

        postRefAsUser(ref)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_USER_URL))
            .andExpect(jsonPath("$.type").value(ErrorConstants.PLUGIN_VALIDATION_TYPE.toString()))
            .andExpect(jsonPath("$.tag").value("plugin/user"))
            .andExpect(jsonPath("$.reason").value("userUrl.sources"))
            .andExpect(jsonPath("$.detail").value("plugin/user: requires exactly one source"));
    }

    @Test
    void testCreateUserUrlWithoutUserTag() throws Exception {
        var ref = createRef("tag:/+user/tester?url=" + URL, "plugin/user", null);
        ref.setSources(new ArrayList<>(List.of(URL)));

        postRefAsUser(ref)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_USER_URL))
            .andExpect(jsonPath("$.reason").value("userUrl.userTag"));
    }

    @Test
    void testCreateUserUrlWithWrongPrefix() throws Exception {
        var ref = createRef(URL + "/user", "plugin/user", null);
        ref.addTag("+user/tester");
        ref.setSources(new ArrayList<>(List.of(URL)));

        postRefAsUser(ref)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_USER_URL))
            .andExpect(jsonPath("$.reason").value("userUrl.prefix"));
    }
}
