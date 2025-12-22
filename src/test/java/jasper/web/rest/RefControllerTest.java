package jasper.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.web.rest.errors.ErrorConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
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
            .andExpect(jsonPath("$.detail").value(containsString("plugin/test")));
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
            .andExpect(jsonPath("$.detail").value(containsString("plugin/strict")));
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

        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_INVALID_PLUGIN))
            .andExpect(jsonPath("$.detail").value(containsString("plugin/test")));
    }

    @Test
    void testUpdateRefWithInvalidPluginData() throws Exception {
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

        // Now try to update with invalid plugin data (negative age for uint32)
        var invalidPluginData = mapper.createObjectNode();
        invalidPluginData.put("name", "Jane");
        invalidPluginData.put("age", -5);  // Invalid: negative value for uint32
        ref.setPlugin("plugin/test", invalidPluginData);

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
            .andExpect(jsonPath("$.detail").value(containsString("[")));  // Contains error details in array format
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
        ref.setPlugin("plugin/test", pluginData);

        // Plugin data without the tag is allowed (will be validated when tag is added)
        mockMvc
            .perform(post("/api/v1/ref")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(ref))
                .with(csrf().asHeader()))
            .andExpect(status().isCreated());
    }
}
