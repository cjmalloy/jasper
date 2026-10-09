package jasper.web.rest.errors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DeactivateSelfException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.DuplicateTagException;
import jasper.errors.FieldError;
import jasper.errors.FreshLoginException;
import jasper.errors.InvalidPatchException;
import jasper.errors.InvalidPluginException;
import jasper.errors.InvalidPluginUserUrlException;
import jasper.errors.InvalidPushException;
import jasper.errors.InvalidQueryException;
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
import jasper.errors.ValidationErrors;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/exception-translator-test")
public class ExceptionTranslatorTestController {

	@GetMapping("/concurrency-failure")
	public void concurrencyFailure() {
		throw new ConcurrencyFailureException("test concurrency failure");
	}

	@GetMapping("/wrapped-concurrency-failure")
	public void wrappedConcurrencyFailure() {
		throw new RuntimeException("wrapper", new RuntimeException("inner", new ConcurrencyFailureException("test concurrency failure")));
	}

	@PostMapping("/method-argument")
	public void methodArgument(@Valid @RequestBody TestDTO testDTO) {}

	@GetMapping("/missing-servlet-request-part")
	public void missingServletRequestPartException(@RequestPart String part) {}

	@GetMapping("/missing-servlet-request-parameter")
	public void missingServletRequestParameterException(@RequestParam String param) {}

	@GetMapping("/type-mismatch")
	public void typeMismatch(@RequestParam int number) {}

	@GetMapping("/access-denied")
	public void accessdenied() {
		throw new AccessDeniedException("test access denied!");
	}

	@GetMapping("/unauthorized")
	public void unauthorized() {
		throw new BadCredentialsException("test authentication failed!");
	}

	@GetMapping("/response-status")
	public void exceptionWithResponseStatus() {
		throw new TestResponseStatusException();
	}

	@GetMapping("/internal-server-error")
	public void internalServerError() {
		throw new RuntimeException();
	}

	@GetMapping("/http-message-conversion")
	public void httpMessageConversion() {
		throw new HttpMessageConversionException("Failed to convert http message");
	}

	@GetMapping("/data-access")
	public void dataAccess() {
		throw new DataAccessResourceFailureException("Database access failed");
	}

	@GetMapping("/internal-server-error-with-package")
	public void internalServerErrorWithPackage() {
		throw new RuntimeException("Error in org.springframework.web package");
	}

	@GetMapping("/bad-request-with-package")
	public void badRequestWithPackage() {
		throw new InvalidTunnelException("Error in jasper.component.TunnelClient");
	}

	@GetMapping("/bad-request-with-de-package")
	public void badRequestWithDePackage() {
		throw new InvalidTunnelException("Error in de.example.TunnelClient");
	}

	@GetMapping("/no-resource")
	public void noResource() throws NoResourceFoundException {
		throw new NoResourceFoundException(HttpMethod.GET, "/api/exception-translator-test/no-resource", "missing.txt");
	}

	@GetMapping("/max-upload-size")
	public void maxUploadSize() {
		throw new MaxUploadSizeExceededException(100);
	}

	@GetMapping("/data-integrity")
	public void dataIntegrity() {
		throw new DataIntegrityViolationException("insert into ref violates not-null constraint");
	}

	@GetMapping("/duplicate-key")
	public void duplicateKey() {
		throw new DuplicateKeyException("duplicate key value violates unique constraint \"ref_pkey\"");
	}

	@GetMapping("/already-exists")
	public void alreadyExists() {
		throw new AlreadyExistsException();
	}

	@GetMapping("/deactivate-self")
	public void deactivateSelf() {
		throw new DeactivateSelfException();
	}

	@GetMapping("/duplicate-modified-date")
	public void duplicateModifiedDate() {
		throw new DuplicateModifiedDateException();
	}

	@GetMapping("/duplicate-tag")
	public void duplicateTag() {
		throw new DuplicateTagException("+user/test");
	}

	@GetMapping("/fresh-login")
	public void freshLogin() {
		throw new FreshLoginException();
	}

	@GetMapping("/invalid-patch")
	public void invalidPatch() {
		throw new InvalidPatchException("Ref", new IllegalArgumentException("Missing path"));
	}

	@GetMapping("/invalid-plugin")
	public void invalidPlugin() {
		throw new InvalidPluginException("plugin/test", ValidationErrors.SCHEMA, List.of(
			new FieldError("/age", "/properties/age/type", "type", "uint32", "age: expected uint32")));
	}

	@GetMapping("/invalid-user-url")
	public void invalidUserUrl() {
		throw new InvalidPluginUserUrlException("plugin/user", ValidationErrors.USER_URL_SOURCES, "requires exactly one source");
	}

	@GetMapping("/invalid-push")
	public void invalidPush() {
		throw new InvalidPushException();
	}

	@GetMapping("/invalid-query")
	public void invalidQuery() {
		throw new InvalidQueryException("a|", "unexpected end of query");
	}

	@GetMapping("/invalid-template")
	public void invalidTemplate() {
		throw new InvalidTemplateException("_config/test", ValidationErrors.SCHEMALESS, "config is not allowed without a template schema");
	}

	@GetMapping("/invalid-tunnel")
	public void invalidTunnel() {
		throw new InvalidTunnelException("Invalid tunnel host");
	}

	@GetMapping("/invalid-user-profile")
	public void invalidUserProfile() {
		throw new InvalidUserProfileException("Invalid user profile");
	}

	@GetMapping("/max-sources")
	public void maxSources() {
		throw new MaxSourcesException(1000, 1001);
	}

	@GetMapping("/modified")
	public void modified() {
		throw new ModifiedException("TestEntity");
	}

	@GetMapping("/not-available")
	public void notAvailable() {
		throw new NotAvailableException();
	}

	@GetMapping("/not-found")
	public void notFound() {
		throw new NotFoundException("Ref https://www.example.com/");
	}

	@GetMapping("/origin-forbidden")
	public void originForbidden() {
		throw new OperationForbiddenOnOriginException("@other");
	}

	@GetMapping("/publish-date")
	public void publishDate() {
		throw new PublishDateException("https://www.example.com/response", Instant.EPOCH, "https://www.example.com/source", Instant.EPOCH.plusSeconds(1));
	}

	@GetMapping("/pull-local")
	public void pullLocal() {
		throw new PullLocalException("");
	}

	@GetMapping("/retryable-tunnel")
	public void retryableTunnel() throws RetryableTunnelException {
		throw new RetryableTunnelException("Tunnel timed out");
	}

	@GetMapping("/scrape-protocol")
	public void scrapeProtocol() {
		throw new ScrapeProtocolException("ftp");
	}

	@GetMapping("/script")
	public void script() throws ScriptException {
		throw new ScriptException("Script failed", "logs");
	}

	@GetMapping("/untrusted-script")
	public void untrustedScript() throws UntrustedScriptException {
		throw new UntrustedScriptException("hash");
	}

	@GetMapping("/too-large")
	public void tooLarge() {
		throw new TooLargeException(1000, 100);
	}

	@GetMapping("/user-tag-in-use")
	public void userTagInUse() {
		throw new UserTagInUseException();
	}

	public static class TestDTO {

		@NotNull
		private String test;

		public String getTest() {
			return test;
		}

		public void setTest(String test) {
			this.test = test;
		}
	}

	@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "test response status")
	@SuppressWarnings("serial")
	public static class TestResponseStatusException extends RuntimeException {}
}
