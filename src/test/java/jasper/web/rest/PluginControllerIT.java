package jasper.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.config.Props;
import jasper.domain.Plugin;
import jasper.repository.PluginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link PluginController}.
 * Tests cross-origin write prevention with Local-Origin header.
 */
@WithMockUser(value = "+user/tester@a", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class PluginControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PluginRepository pluginRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Props props;

	@BeforeEach
	void setup() {
		pluginRepository.deleteAll();
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

		assertThat(pluginRepository.count()).isZero();
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

		assertThat(pluginRepository.count()).isZero();
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

		var existing = pluginRepository.findOneByQualifiedTag("plugin/test@b");
		assertThat(existing).isPresent();
		assertThat(existing.get().getName()).isNull();
	}

	@Test
	void testGetPluginWithArrayDefaultsSerializedCorrectly() throws Exception {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setDefaults(objectMapper.readTree("[1, 2, 3]"));
		pluginRepository.save(plugin);

		mockMvc
			.perform(get("/api/v1/plugin")
				.param("tag", "plugin/test"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaults").isArray())
			.andExpect(jsonPath("$.defaults[0]").value(1))
			.andExpect(jsonPath("$.defaults[1]").value(2))
			.andExpect(jsonPath("$.defaults[2]").value(3));
	}

	@Test
	void testGetPluginWithScalarDefaultsSerializedCorrectly() throws Exception {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setDefaults(objectMapper.readTree("\"scalar-value\""));
		pluginRepository.save(plugin);

		mockMvc
			.perform(get("/api/v1/plugin")
				.param("tag", "plugin/test"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaults").value("scalar-value"));
	}

	@Test
	void testMergePluginReturnsCursor() throws Exception {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin = pluginRepository.save(plugin);
		// The default user is used for requests, so set the admin user by header
		props.setAllowUserTagHeader(true);
		props.setAllowUserRoleHeader(true);
		try {
			var cursor = mockMvc
				.perform(patch("/api/v1/plugin")
					.contentType("application/merge-patch+json")
					.content("{\"name\": \"Test\"}")
					.param("tag", "plugin/test")
					.param("cursor", plugin.getModified().toString())
					.header("User-Tag", "+user/tester")
					.header("User-Role", "ROLE_ADMIN")
					.with(csrf().asHeader()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

			var updated = pluginRepository.findOneByQualifiedTag("plugin/test");
			assertThat(updated).isPresent();
			assertThat(updated.get().getName()).isEqualTo("Test");
			assertThat(objectMapper.readValue(cursor, Instant.class)).isCloseTo(updated.get().getModified(), within(1, ChronoUnit.MICROS));
		} finally {
			props.setAllowUserTagHeader(false);
			props.setAllowUserRoleHeader(false);
		}
	}

	@Test
	void testPageWithTagLen() throws Exception {
		mockMvc
			.perform(get("/api/v1/plugin/page")
				.param("filter", "tag:len/equals/1")
				.param("sort", "tag:len"))
			.andExpect(status().isOk());
	}

	@Test
	void testPageWithRefColumnFilterShouldFail() throws Exception {
		for (var value : List.of("published/after/2026-01-01T00:00:00Z", "tags:len/after/0", "sources:len/after/0", "metadata->responses/after/0", "tags->plugin/title/equals/a")) {
			mockMvc
				.perform(get("/api/v1/plugin/page")
					.param("filter", value))
				.andExpect(status().isBadRequest());
		}
	}
}
