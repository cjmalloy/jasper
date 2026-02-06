package jasper.web.rest;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link RefController}.
 * Tests validation of fully qualified tags in Ref operations.
 */
@WithMockUser(value = "+user/tester", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class RefControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefRepository refRepository;

	@Autowired
	private JsonMapper mapper;

	private static final String URL = "https://www.example.com/";

	@BeforeEach
	void setup() {
		refRepository.deleteAll();
	}

	@Test
	void testCreateRefWithFullyQualifiedTagShouldFail() throws Exception {
		// Test that creating a Ref with a fully qualified tag (containing @) in tags list fails validation
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("test@origin")); // Fully qualified tag with @

		mockMvc
			.perform(post("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(ref))
				.with(csrf().asHeader()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void testCreateRefWithFullyQualifiedTagAtSignOnlyShouldFail() throws Exception {
		// Test that creating a Ref with a tag ending in @ fails validation
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("test@")); // Tag with @ at end

		mockMvc
			.perform(post("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(ref))
				.with(csrf().asHeader()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void testCreateRefWithFullyQualifiedTagWithFullOriginShouldFail() throws Exception {
		// Test that creating a Ref with a fully qualified tag with full origin fails validation
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("test@remote.example.com")); // Fully qualified tag with full origin

		mockMvc
			.perform(post("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(ref))
				.with(csrf().asHeader()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void testCreateRefWithValidLocalTagShouldSucceed() throws Exception {
		// Test that creating a Ref with a valid local tag (no @) succeeds
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("test")); // Valid local tag

		mockMvc
			.perform(post("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(ref))
				.with(csrf().asHeader()))
			.andExpect(status().isCreated());
	}

	@Test
	void testCreateRefWithMultipleTagsIncludingFullyQualifiedShouldFail() throws Exception {
		// Test that creating a Ref with multiple tags where one is fully qualified fails
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(List.of("valid", "test@origin", "another")); // One fully qualified tag

		mockMvc
			.perform(post("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(ref))
				.with(csrf().asHeader()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void testUpdateRefWithFullyQualifiedTagShouldFail() throws Exception {
		// First create a valid ref
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("public")));
		refRepository.save(ref);

		// Try to update with a fully qualified tag
		ref.setTags(List.of("test@origin"));

		mockMvc
			.perform(put("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(ref))
				.with(csrf().asHeader()))
			.andExpect(status().isBadRequest());
	}
}
