package jasper.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.Ext;
import jasper.repository.ExtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static jasper.repository.filter.Query.QUERY_LEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ExtController}.
 * Tests cross-origin write prevention with Local-Origin header.
 */
@WithMockUser(value = "+user/tester@a", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class ExtControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ExtRepository extRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ConfigCache configCache;

	@BeforeEach
	void setup() {
		extRepository.deleteAll();
		configCache.clearUserCache();
		configCache.clearPluginCache();
		configCache.clearTemplateCache();
	}

	@Test
	void testCreateExtWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var ext = new Ext();
		ext.setTag("ext");
		ext.setOrigin("@b");

		mockMvc
			.perform(post("/api/v1/ext")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(ext))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		assertThat(extRepository.count()).isZero();
	}

	@Test
	void testCreateExtWithParentOriginFromSubOriginShouldFail() throws Exception {
		var ext = new Ext();
		ext.setTag("ext");
		ext.setOrigin("@a");

		mockMvc
			.perform(post("/api/v1/ext")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(ext))
				.header("Local-Origin", "@a.b")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		assertThat(extRepository.count()).isZero();
	}

	@Test
	void testUpdateExtWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var ext = new Ext();
		ext.setTag("ext");
		ext.setOrigin("@b");
		extRepository.save(ext);

		ext.setName("updated");

		mockMvc
			.perform(put("/api/v1/ext")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(ext))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		var existing = extRepository.findOneByQualifiedTag("ext@b");
		assertThat(existing).isPresent();
		assertThat(existing.get().getName()).isNull();
	}

	@Test
	void testPageWithMalformedQueryShouldFail() throws Exception {
		for (var query : List.of("!", "a:!", "!|a", "!:a", "()", "a:()", "!()", "(a", "a)", "a!", "a!b", "(".repeat(QUERY_LEN))) {
			mockMvc
				.perform(get("/api/v1/ext/page")
					.param("query", query))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void testPageWithNegatedGroupQuery() throws Exception {
		mockMvc
			.perform(get("/api/v1/ext/page")
				.param("query", "!(a|b):!(!c)"))
			.andExpect(status().isOk());
	}

	@Test
	void testPageAndCountWithConfigFilters() throws Exception {
		for (var path : List.of("/api/v1/ext/page", "/api/v1/ext/count")) {
			mockMvc
				.perform(get(path)
					.param("filter", "config->pinned:len/after/0")
					.param("filter", "config->order:num/before/3")
					.param("filter", "tag:len/equals/1"))
				.andExpect(status().isOk());
			for (var invalid : List.of("external->ids:len/after/0", "published/after/2026-01-01T00:00:00Z", "config/after/1", "config->order:num/after/abc", "config->order:num/3")) {
				mockMvc
					.perform(get(path).param("filter", invalid))
					.andExpect(status().isBadRequest());
			}
		}
	}

	@Test
	void testPageWithTagLen() throws Exception {
		mockMvc
			.perform(get("/api/v1/ext/page")
				.param("filter", "tag:len/equals/1")
				.param("sort", "tag:len"))
			.andExpect(status().isOk());
	}

	@Test
	void testPageWithRefColumnFilterShouldFail() throws Exception {
		for (var value : List.of("published/after/2026-01-01T00:00:00Z", "tags:len/after/0", "sources:len/after/0", "metadata->responses/after/0", "tags->plugin/title/equals/a")) {
			mockMvc
				.perform(get("/api/v1/ext/page")
					.param("filter", value))
				.andExpect(status().isBadRequest());
		}
	}
}
