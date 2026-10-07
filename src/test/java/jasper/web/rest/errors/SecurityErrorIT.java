package jasper.web.rest.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

import static jasper.web.rest.errors.ErrorConstants.ACCESS_VIOLATION_TYPE;
import static jasper.web.rest.errors.ErrorConstants.ERR_ACCESS_DENIED;
import static jasper.web.rest.errors.ErrorConstants.ERR_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errors raised by the security filter chain before MVC are routed through {@link ExceptionTranslator}.
 */
@AutoConfigureMockMvc
@IntegrationTest
class SecurityErrorIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	SecurityProblemSupport securityErrors;

	@Autowired
	ObjectMapper mapper;

	@Test
	void testMissingCsrf() throws Exception {
		mockMvc
			.perform(post("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://www.example.com/csrf\"}"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.message").value(ERR_ACCESS_DENIED))
			.andExpect(jsonPath("$.type").value(ACCESS_VIOLATION_TYPE.toString()))
			.andExpect(jsonPath("$.path").value("/api/v1/ref"));
	}

	@Test
	void testAuthenticationEntryPoint() throws Exception {
		var request = new MockHttpServletRequest("GET", "/api/v1/ref");
		var response = new MockHttpServletResponse();
		securityErrors.commence(request, response, new InsufficientAuthenticationException("Full authentication is required"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		var body = mapper.readTree(response.getContentAsString());
		assertThat(body.get("message").asText()).isEqualTo(ERR_UNAUTHORIZED);
		assertThat(body.get("type").asText()).isEqualTo(ACCESS_VIOLATION_TYPE.toString());
		assertThat(body.get("path").asText()).isEqualTo("/api/v1/ref");
	}
}
