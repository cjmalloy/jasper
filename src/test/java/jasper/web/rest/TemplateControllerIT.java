package jasper.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.domain.Template;
import jasper.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link TemplateController}.
 * Tests cross-origin write prevention with Local-Origin header.
 */
@WithMockUser(value = "+user/tester@a", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class TemplateControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setup() {
		templateRepository.deleteAll();
	}

	@Test
	void testCreateTemplateWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var template = new Template();
		template.setTag("template");
		template.setOrigin("@b");

		mockMvc
			.perform(post("/api/v1/template")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(template))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		assertThat(templateRepository.count()).isZero();
	}

	@Test
	void testCreateTemplateWithParentOriginFromSubOriginShouldFail() throws Exception {
		var template = new Template();
		template.setTag("template");
		template.setOrigin("@a");

		mockMvc
			.perform(post("/api/v1/template")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(template))
				.header("Local-Origin", "@a.b")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		assertThat(templateRepository.count()).isZero();
	}

	@Test
	void testUpdateTemplateWithDifferentOriginThanLocalOriginHeaderShouldFail() throws Exception {
		var template = new Template();
		template.setTag("template");
		template.setOrigin("@b");
		templateRepository.save(template);

		template.setName("updated");

		mockMvc
			.perform(put("/api/v1/template")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(template))
				.header("Local-Origin", "@a")
				.with(csrf().asHeader()))
			.andExpect(status().isForbidden());

		var existing = templateRepository.findOneByQualifiedTag("template@b");
		assertThat(existing).isPresent();
		assertThat(existing.get().getName()).isNull();
	}

	@Test
	void testPageWithTagLen() throws Exception {
		mockMvc
			.perform(get("/api/v1/template/page")
				.param("filter", "tag:len/equals/1")
				.param("sort", "tag:len"))
			.andExpect(status().isOk());
	}

	@Test
	void testPageWithRefColumnFilterShouldFail() throws Exception {
		for (var value : List.of("published/after/2026-01-01T00:00:00Z", "tags:len/after/0", "sources:len/after/0", "metadata->responses/after/0", "tags->plugin/title/equals/a")) {
			mockMvc
				.perform(get("/api/v1/template/page")
					.param("filter", value))
				.andExpect(status().isBadRequest());
		}
	}
}
