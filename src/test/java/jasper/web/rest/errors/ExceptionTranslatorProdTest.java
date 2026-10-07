package jasper.web.rest.errors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production profile error message sanitization.
 * Runs {@link ExceptionTranslator} against a standalone MockMvc so no database is required.
 */
class ExceptionTranslatorProdTest {

	private static final String BASE = "/api/exception-translator-test/";
	private static final String UNEXPECTED = "Unexpected runtime exception";
	private static final String DATA_ACCESS = "Failure during data access";
	private static final String CONVERSION = "Unable to convert http message";

	private MockMvc mockMvc;

	@BeforeEach
	void setup() {
		var env = new MockEnvironment();
		env.setActiveProfiles("prod");
		mockMvc = MockMvcBuilders
			.standaloneSetup(new ExceptionTranslatorTestController())
			.setControllerAdvice(new ExceptionTranslator(env))
			.build();
	}

	/**
	 * Every 5xx path: unmapped exceptions plus every mapped exception with a 5xx status.
	 */
	static Stream<Arguments> serverErrors() {
		return Stream.concat(
			Stream.of(
				Arguments.of("internal-server-error", 500, UNEXPECTED),
				Arguments.of("internal-server-error-with-package", 500, UNEXPECTED),
				Arguments.of("http-message-conversion", 500, CONVERSION),
				Arguments.of("data-access", 500, DATA_ACCESS)
			),
			ExceptionTranslatorIT.mappedExceptions()
				.map(Arguments::get)
				.filter(a -> ((HttpStatus) a[2]).is5xxServerError())
				.map(a -> Arguments.of(a[1], ((HttpStatus) a[2]).value(), UNEXPECTED))
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("serverErrors")
	void testServerErrorDetailsHidden(String path, int status, String detail) throws Exception {
		var body = mockMvc
			.perform(get(BASE + path))
			.andExpect(status().is(status))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(detail))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("Exception:", "\tat ", "org.", "java.", "jasper.", "Script failed", "Database access failed");
	}

	static Stream<Arguments> clientErrors() {
		return Stream.of(
			// SQL is hidden
			Arguments.of("data-integrity", 409, ErrorConstants.ERR_DATA_INTEGRITY, DATA_ACCESS),
			Arguments.of("duplicate-key", 409, ErrorConstants.ERR_DUPLICATE_KEY, DATA_ACCESS),
			// Package names are hidden
			Arguments.of("bad-request-with-package", 400, ErrorConstants.ERR_INVALID_TUNNEL, UNEXPECTED),
			// Plugin and template validation details stay visible
			Arguments.of("invalid-plugin", 400, ErrorConstants.ERR_INVALID_PLUGIN, "plugin/test: age: expected uint32"),
			Arguments.of("invalid-template", 400, ErrorConstants.ERR_INVALID_TEMPLATE, "_config/test: config is not allowed without a template schema"),
			// URLs are not package names
			Arguments.of("not-found", 404, ErrorConstants.ERR_NOT_FOUND, "Ref https://www.example.com/")
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("clientErrors")
	void testClientErrorDetails(String path, int status, String code, String detail) throws Exception {
		mockMvc
			.perform(get(BASE + path))
			.andExpect(status().is(status))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(code))
			.andExpect(jsonPath("$.detail").value(detail));
	}

	@Test
	void testValidationErrorsVisible() throws Exception {
		mockMvc
			.perform(get(BASE + "invalid-plugin"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.tag").value("plugin/test"))
			.andExpect(jsonPath("$.reason").value("schema"))
			.andExpect(jsonPath("$.errors[0].path").value("/age"))
			.andExpect(jsonPath("$.errors[0].code").value("type"));
		mockMvc
			.perform(get(BASE + "invalid-user-url"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.reason").value("userUrl.sources"))
			.andExpect(jsonPath("$.detail").value("plugin/user: requires exactly one source"));
	}
}
