package jasper.web.rest.errors;

import jasper.IntegrationTest;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DeactivateSelfException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.DuplicateTagException;
import jasper.errors.FreshLoginException;
import jasper.errors.InvalidPatchException;
import jasper.errors.InvalidPluginException;
import jasper.errors.InvalidPluginUserUrlException;
import jasper.errors.InvalidPushException;
import jasper.errors.InvalidTemplateException;
import jasper.errors.InvalidTunnelException;
import jasper.errors.InvalidUserProfileException;
import jasper.errors.MaxSourcesException;
import jasper.errors.ModifiedException;
import jasper.errors.NotAvailableException;
import jasper.errors.NotFoundException;
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.errors.PublishDateException;
import jasper.errors.PullLocalException;
import jasper.errors.RetryableTunnelException;
import jasper.errors.ScrapeProtocolException;
import jasper.errors.ScriptException;
import jasper.errors.TooLargeException;
import jasper.errors.UntrustedScriptException;
import jasper.errors.UserTagInUseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jasper.web.rest.errors.ErrorConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests {@link ExceptionTranslator} controller advice.
 */
@WithMockUser
@AutoConfigureMockMvc
@IntegrationTest
class ExceptionTranslatorIT {

	private static final String BASE = "/api/exception-translator-test/";

	@Autowired
	private MockMvc mockMvc;

	/**
	 * One row per mapped exception thrown directly from {@link ExceptionTranslatorTestController}:
	 * exception, endpoint, status, code, type, detail (null if none).
	 */
	static Stream<Arguments> mappedExceptions() {
		return Stream.of(
			Arguments.of(ConcurrencyFailureException.class, "concurrency-failure", HttpStatus.CONFLICT, ERR_OPTIMISTIC_LOCK, CONFLICT_TYPE, "test concurrency failure"),
			Arguments.of(AccessDeniedException.class, "access-denied", HttpStatus.FORBIDDEN, ERR_ACCESS_DENIED, ACCESS_VIOLATION_TYPE, "test access denied!"),
			Arguments.of(AuthenticationException.class, "unauthorized", HttpStatus.UNAUTHORIZED, ERR_UNAUTHORIZED, ACCESS_VIOLATION_TYPE, "test authentication failed!"),
			Arguments.of(NoResourceFoundException.class, "no-resource", HttpStatus.NOT_FOUND, ERR_NOT_FOUND, NOT_FOUND_TYPE, "No static resource missing.txt for request '/api/exception-translator-test/no-resource'."),
			Arguments.of(MaxUploadSizeExceededException.class, "max-upload-size", HttpStatus.PAYLOAD_TOO_LARGE, ERR_TOO_LARGE, SIZE_ERROR_TYPE, "Maximum upload size of 100 bytes exceeded"),
			Arguments.of(DataIntegrityViolationException.class, "data-integrity", HttpStatus.CONFLICT, ERR_DATA_INTEGRITY, CONFLICT_TYPE, "insert into ref violates not-null constraint"),
			Arguments.of(DuplicateKeyException.class, "duplicate-key", HttpStatus.CONFLICT, ERR_DUPLICATE_KEY, DUPLICATE_KEY_TYPE, "duplicate key value violates unique constraint \"ref_pkey\""),
			Arguments.of(AlreadyExistsException.class, "already-exists", HttpStatus.CONFLICT, ERR_ALREADY_EXISTS, CONFLICT_TYPE, "Already exists"),
			Arguments.of(DeactivateSelfException.class, "deactivate-self", HttpStatus.FORBIDDEN, ERR_DEACTIVATE_SELF, USER_ERROR_TYPE, "You cannot deactivate your own account."),
			Arguments.of(DuplicateModifiedDateException.class, "duplicate-modified-date", HttpStatus.CONFLICT, ERR_DUPLICATE_MODIFIED_DATE, DUPLICATE_KEY_TYPE, "Modified date must be unique."),
			Arguments.of(DuplicateTagException.class, "duplicate-tag", HttpStatus.CONFLICT, ERR_DUPLICATE_TAG, DUPLICATE_KEY_TYPE, "Duplicate tag +user/test"),
			Arguments.of(FreshLoginException.class, "fresh-login", HttpStatus.FORBIDDEN, ERR_FRESH_LOGIN, USER_ERROR_TYPE, "Requires reauthorization. Please log again to access."),
			Arguments.of(InvalidPatchException.class, "invalid-patch", HttpStatus.BAD_REQUEST, ERR_INVALID_PATCH, CONSTRAINT_VIOLATION_TYPE, "Missing path"),
			Arguments.of(InvalidPluginException.class, "invalid-plugin", HttpStatus.BAD_REQUEST, ERR_INVALID_PLUGIN, PLUGIN_VALIDATION_TYPE, "plugin/test: age: expected uint32"),
			Arguments.of(InvalidPluginUserUrlException.class, "invalid-user-url", HttpStatus.BAD_REQUEST, ERR_INVALID_USER_URL, PLUGIN_VALIDATION_TYPE, "plugin/user: requires exactly one source"),
			Arguments.of(InvalidPushException.class, "invalid-push", HttpStatus.BAD_REQUEST, ERR_INVALID_PUSH, CONSTRAINT_VIOLATION_TYPE, "Push contains invalid data."),
			Arguments.of(InvalidTemplateException.class, "invalid-template", HttpStatus.BAD_REQUEST, ERR_INVALID_TEMPLATE, TEMPLATE_VALIDATION_TYPE, "_config/test: config is not allowed without a template schema"),
			Arguments.of(InvalidTunnelException.class, "invalid-tunnel", HttpStatus.BAD_REQUEST, ERR_INVALID_TUNNEL, PROTOCOL_ERROR_TYPE, "Invalid tunnel host"),
			Arguments.of(InvalidUserProfileException.class, "invalid-user-profile", HttpStatus.BAD_REQUEST, ERR_INVALID_USER_PROFILE, USER_ERROR_TYPE, "Invalid user profile"),
			Arguments.of(MaxSourcesException.class, "max-sources", HttpStatus.BAD_REQUEST, ERR_MAX_SOURCES, SIZE_ERROR_TYPE, "Max count is set to 1000. Ref contains 1001 sources."),
			Arguments.of(ModifiedException.class, "modified", HttpStatus.CONFLICT, ERR_MODIFIED, CONFLICT_TYPE, "TestEntity already modified"),
			Arguments.of(NotAvailableException.class, "not-available", HttpStatus.SERVICE_UNAVAILABLE, ERR_NOT_AVAILABLE, UNAVAILABLE_TYPE, null),
			Arguments.of(NotFoundException.class, "not-found", HttpStatus.NOT_FOUND, ERR_NOT_FOUND, NOT_FOUND_TYPE, "Ref https://www.example.com/"),
			Arguments.of(OperationForbiddenOnOriginException.class, "origin-forbidden", HttpStatus.FORBIDDEN, ERR_ORIGIN_FORBIDDEN, ORIGIN_ERROR_TYPE, "Origin @other is not whitelisted for this operation."),
			Arguments.of(PublishDateException.class, "publish-date", HttpStatus.CONFLICT, ERR_PUBLISH_DATE, DATE_ERROR_TYPE, "Source https://www.example.com/source (1970-01-01T00:00:01Z) must predate response https://www.example.com/response (1970-01-01T00:00:00Z)"),
			Arguments.of(PullLocalException.class, "pull-local", HttpStatus.FORBIDDEN, ERR_PULL_LOCAL, ORIGIN_ERROR_TYPE, "Can't pull into local origin (). You must pull into a nested origin."),
			Arguments.of(RetryableTunnelException.class, "retryable-tunnel", HttpStatus.REQUEST_TIMEOUT, ERR_TUNNEL_TIMEOUT, PROTOCOL_ERROR_TYPE, "Tunnel timed out"),
			Arguments.of(ScrapeProtocolException.class, "scrape-protocol", HttpStatus.BAD_REQUEST, ERR_SCRAPE_PROTOCOL, PROTOCOL_ERROR_TYPE, "Cannot scrape protocol: ftp"),
			Arguments.of(ScriptException.class, "script", HttpStatus.INTERNAL_SERVER_ERROR, ERR_SCRIPT, SCRIPT_ERROR_TYPE, "Script failed"),
			Arguments.of(UntrustedScriptException.class, "untrusted-script", HttpStatus.FORBIDDEN, ERR_UNTRUSTED_SCRIPT, SCRIPT_ERROR_TYPE, null),
			Arguments.of(TooLargeException.class, "too-large", HttpStatus.PAYLOAD_TOO_LARGE, ERR_TOO_LARGE, SIZE_ERROR_TYPE, "You requested 1000 entities, but the max is 100."),
			Arguments.of(UserTagInUseException.class, "user-tag-in-use", HttpStatus.CONFLICT, ERR_USER_TAG_IN_USE, CONFLICT_TYPE, "User tag already in use by another user.")
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("mappedExceptions")
	void testMappedException(Class<?> exception, String path, HttpStatus status, String code, URI type, String detail) throws Exception {
		var result = mockMvc
			.perform(get(BASE + path))
			.andExpect(status().is(status.value()))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(status.value()))
			.andExpect(jsonPath("$.message").value(code))
			.andExpect(jsonPath("$.type").value(type.toString()))
			.andExpect(jsonPath("$.path").value(BASE + path));
		if (detail == null) {
			result.andExpect(jsonPath("$.detail").doesNotExist());
		} else {
			result.andExpect(jsonPath("$.detail").value(detail));
		}
	}

	@Test
	void testEveryMappedExceptionIsTested() {
		var tested = mappedExceptions().map(a -> a.get()[0]).collect(Collectors.toSet());
		// Covered by the request tests below
		tested.addAll(Stream.of(
			MethodArgumentNotValidException.class,
			HttpMessageNotReadableException.class,
			MissingServletRequestParameterException.class,
			MissingServletRequestPartException.class,
			MethodArgumentTypeMismatchException.class,
			HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class
		).collect(Collectors.toSet()));
		assertThat(tested).containsExactlyInAnyOrderElementsOf(ExceptionTranslator.ERROR_MAPPINGS.keySet());
	}

	@Test
	void testInvalidPluginStructuredErrors() throws Exception {
		mockMvc
			.perform(get(BASE + "invalid-plugin"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.tag").value("plugin/test"))
			.andExpect(jsonPath("$.reason").value("schema"))
			.andExpect(jsonPath("$.truncated").value(false))
			.andExpect(jsonPath("$.errors[0].path").value("/age"))
			.andExpect(jsonPath("$.errors[0].schemaPath").value("/properties/age/type"))
			.andExpect(jsonPath("$.errors[0].code").value("type"))
			.andExpect(jsonPath("$.errors[0].expected").value("uint32"))
			.andExpect(jsonPath("$.errors[0].message").value("age: expected uint32"));
	}

	@Test
	void testInvalidUserUrlReason() throws Exception {
		mockMvc
			.perform(get(BASE + "invalid-user-url"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.tag").value("plugin/user"))
			.andExpect(jsonPath("$.reason").value("userUrl.sources"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void testWrappedConcurrencyFailure() throws Exception {
		mockMvc
			.perform(get(BASE + "wrapped-concurrency-failure"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_OPTIMISTIC_LOCK))
			.andExpect(jsonPath("$.type").value(CONFLICT_TYPE.toString()));
	}

	@Test
	void testMethodArgumentNotValid() throws Exception {
		mockMvc
			.perform(post(BASE + "method-argument").content("{}").contentType(MediaType.APPLICATION_JSON).with(csrf().asHeader()))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_VALIDATION))
			.andExpect(jsonPath("$.type").value(CONSTRAINT_VIOLATION_TYPE.toString()))
			.andExpect(jsonPath("$.title").value("Method argument not valid"))
			.andExpect(jsonPath("$.detail").value(containsString("testDTO")))
			.andExpect(jsonPath("$.fieldErrors.[0].objectName").value("test"))
			.andExpect(jsonPath("$.fieldErrors.[0].field").value("test"))
			.andExpect(jsonPath("$.fieldErrors.[0].message").value("must not be null"));
	}

	@Test
	void testMessageNotReadable() throws Exception {
		mockMvc
			.perform(post(BASE + "method-argument").content("{").contentType(MediaType.APPLICATION_JSON).with(csrf().asHeader()))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_MESSAGE_NOT_READABLE))
			.andExpect(jsonPath("$.type").value(REQUEST_ERROR_TYPE.toString()))
			.andExpect(jsonPath("$.detail").isNotEmpty());
	}

	@Test
	void testMediaTypeNotSupported() throws Exception {
		mockMvc
			.perform(post(BASE + "method-argument").content("test").contentType(MediaType.TEXT_PLAIN).with(csrf().asHeader()))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_MEDIA_TYPE_NOT_SUPPORTED))
			.andExpect(jsonPath("$.type").value(REQUEST_ERROR_TYPE.toString()))
			.andExpect(jsonPath("$.detail").value("Content-Type 'text/plain;charset=UTF-8' is not supported"));
	}

	@Test
	void testMissingServletRequestPartException() throws Exception {
		mockMvc
			.perform(get(BASE + "missing-servlet-request-part"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_MISSING_PARAMETER))
			.andExpect(jsonPath("$.type").value(REQUEST_ERROR_TYPE.toString()))
			.andExpect(jsonPath("$.detail").value("Required part 'part' is not present."));
	}

	@Test
	void testMissingServletRequestParameterException() throws Exception {
		mockMvc
			.perform(get(BASE + "missing-servlet-request-parameter"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_MISSING_PARAMETER))
			.andExpect(jsonPath("$.type").value(REQUEST_ERROR_TYPE.toString()))
			.andExpect(jsonPath("$.detail").value("Required request parameter 'param' for method parameter type String is not present"));
	}

	@Test
	void testTypeMismatch() throws Exception {
		mockMvc
			.perform(get(BASE + "type-mismatch").param("number", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_TYPE_MISMATCH))
			.andExpect(jsonPath("$.type").value(REQUEST_ERROR_TYPE.toString()))
			.andExpect(jsonPath("$.detail").value(containsString("abc")));
	}

	@Test
	void testMethodNotSupported() throws Exception {
		mockMvc
			.perform(post(BASE + "access-denied").with(csrf().asHeader()))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value(ERR_METHOD_NOT_SUPPORTED))
			.andExpect(jsonPath("$.type").value(REQUEST_ERROR_TYPE.toString()))
			.andExpect(jsonPath("$.detail").value("Request method 'POST' is not supported"));
	}

	@Test
	void testExceptionWithResponseStatus() throws Exception {
		mockMvc
			.perform(get(BASE + "response-status"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value("error.http.400"))
			.andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString()))
			.andExpect(jsonPath("$.title").value("test response status"));
	}

	@Test
	void testInternalServerError() throws Exception {
		mockMvc
			.perform(get(BASE + "internal-server-error"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value("error.http.500"))
			.andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString()))
			.andExpect(jsonPath("$.title").value("Internal Server Error"));
	}

	@Test
	void testHttpMessageConversionExceptionInDev() throws Exception {
		// In dev/default profiles, detailed error messages should be shown
		mockMvc
			.perform(get(BASE + "http-message-conversion"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value("error.http.500"))
			.andExpect(jsonPath("$.detail").value("Failed to convert http message"));
	}

	@Test
	void testDataAccessExceptionInDev() throws Exception {
		// In dev/default profiles, detailed error messages should be shown
		mockMvc
			.perform(get(BASE + "data-access"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value("error.http.500"))
			.andExpect(jsonPath("$.detail").value("Database access failed"));
	}

	@Test
	void testInternalServerErrorWithPackageNameInDev() throws Exception {
		// In dev/default profiles, even messages with package names should be shown
		mockMvc
			.perform(get(BASE + "internal-server-error-with-package"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.message").value("error.http.500"))
			.andExpect(jsonPath("$.detail").value("Error in org.springframework.web package"));
	}
}
