package jasper.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.repository.PluginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link PluginController}.
 * Tests cross-origin write prevention with Local-Origin header.
 */
@WithMockUser(value = "+user/tester", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
@TestPropertySource(properties = "jasper.default-role=ROLE_ADMIN")
class PluginControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PluginRepository pluginRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setup() {
		pluginRepository.deleteAll();
	}

	@Test
	void testCreatePluginWithLocalOriginHeaderShouldSucceed() throws Exception {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("@a");

		mockMvc
			.perform(post("/api/v1/plugin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(plugin))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isCreated());
	}

	@Test
	void testCreatePluginWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("@b");

		mockMvc
			.perform(post("/api/v1/plugin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(plugin))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());
	}

	@Test
	void testCreatePluginWithParentOriginFromSubOriginShouldFail() throws Exception {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("@a");

		mockMvc
			.perform(post("/api/v1/plugin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(plugin))
				.header("Local-Origin", "@a.b")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());
	}

	@Test
	void testUpdatePluginWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setOrigin("@b");
		pluginRepository.save(plugin);

		plugin.setName("updated");

		mockMvc
			.perform(put("/api/v1/plugin")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(plugin))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());
	}
}
