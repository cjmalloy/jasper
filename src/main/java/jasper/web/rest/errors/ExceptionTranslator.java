package jasper.web.rest.errors;

import jakarta.servlet.http.HttpServletRequest;
import jasper.errors.*;
import jasper.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures.
 * The error response follows RFC7807 - Problem Details for HTTP APIs (https://tools.ietf.org/html/rfc7807).
 */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

	private static final String FIELD_ERRORS_KEY = "fieldErrors";
	private static final String MESSAGE_KEY = "message";
	private static final String PATH_KEY = "path";
	private static final boolean CASUAL_CHAIN_ENABLED = false;

	private final Environment env;

	public ExceptionTranslator(Environment env) {
		this.env = env;
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleAnyException(Throwable ex, NativeWebRequest request) {
		ProblemDetailWithCause pdCause = wrapAndCustomizeProblem(ex, request);
		return handleExceptionInternal((Exception) ex, pdCause, buildHeaders(ex), HttpStatusCode.valueOf(pdCause.getStatus()), request);
	}

	@Nullable
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
		Exception ex,
		@Nullable Object body,
		HttpHeaders headers,
		HttpStatusCode statusCode,
		WebRequest request
	) {
		body = body == null ? wrapAndCustomizeProblem((Throwable) ex, (NativeWebRequest) request) : body;
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	protected ProblemDetailWithCause wrapAndCustomizeProblem(Throwable ex, NativeWebRequest request) {
		return customizeProblem(getProblemDetailWithCause(ex), ex, request);
	}

	private ProblemDetailWithCause getProblemDetailWithCause(Throwable ex) {
		if (
			ex instanceof ErrorResponseException exp && exp.getBody() instanceof ProblemDetailWithCause problemDetailWithCause
		) return problemDetailWithCause;
		return ProblemDetailWithCauseBuilder.instance().withStatus(toStatus(ex).value()).build();
	}

	protected ProblemDetailWithCause customizeProblem(ProblemDetailWithCause problem, Throwable err, NativeWebRequest request) {
		if (problem.getStatus() <= 0) problem.setStatus(toStatus(err));

		if (problem.getType() == null || problem.getType().equals(URI.create("about:blank"))) problem.setType(getMappedType(err));

		// higher precedence to Custom/ResponseStatus types
		String title = extractTitle(err, problem.getStatus());
		String problemTitle = problem.getTitle();
		if (problemTitle == null || !problemTitle.equals(title)) {
			problem.setTitle(title);
		}

		if (problem.getDetail() == null) {
			// higher precedence to cause
			problem.setDetail(getCustomizedErrorDetails(err));
		}

		Map<String, Object> problemProperties = problem.getProperties();
		if (problemProperties == null || !problemProperties.containsKey(MESSAGE_KEY)) problem.setProperty(
			MESSAGE_KEY,
			getMappedMessageKey(err) != null ? getMappedMessageKey(err) : "error.http." + problem.getStatus()
		);

		if (problemProperties == null || !problemProperties.containsKey(PATH_KEY)) problem.setProperty(PATH_KEY, getPathValue(request));

		if (
			(err instanceof MethodArgumentNotValidException fieldException) &&
				(problemProperties == null || !problemProperties.containsKey(FIELD_ERRORS_KEY))
		) problem.setProperty(FIELD_ERRORS_KEY, getFieldErrors(fieldException));

		problem.setCause(buildCause(err.getCause(), request).orElse(null));

		return problem;
	}

	private String extractTitle(Throwable err, int statusCode) {
		return getCustomizedTitle(err) != null ? getCustomizedTitle(err) : extractTitleForResponseStatus(err, statusCode);
	}

	private List<FieldErrorVM> getFieldErrors(MethodArgumentNotValidException ex) {
		return ex
			.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(f ->
				new FieldErrorVM(
					f.getObjectName().replaceFirst("DTO$", ""),
					f.getField(),
					StringUtils.isNotBlank(f.getDefaultMessage()) ? f.getDefaultMessage() : f.getCode()
				)
			)
			.toList();
	}

	private String extractTitleForResponseStatus(Throwable err, int statusCode) {
		ResponseStatus specialStatus = extractResponseStatus(err);
		return specialStatus == null ? HttpStatus.valueOf(statusCode).getReasonPhrase() : specialStatus.reason();
	}

	private String extractURI(NativeWebRequest request) {
		HttpServletRequest nativeRequest = request.getNativeRequest(HttpServletRequest.class);
		return nativeRequest != null ? nativeRequest.getRequestURI() : StringUtils.EMPTY;
	}

	private HttpStatus toStatus(final Throwable throwable) {
		// Let the ErrorResponse take this responsibility
		if (throwable instanceof ErrorResponse err) return HttpStatus.valueOf(err.getBody().getStatus());

		return Optional
			.ofNullable(getMappedStatus(throwable))
			.orElse(
				Optional.ofNullable(resolveResponseStatus(throwable)).map(ResponseStatus::value).orElse(HttpStatus.INTERNAL_SERVER_ERROR)
			);
	}

	private ResponseStatus extractResponseStatus(final Throwable throwable) {
		return Optional.ofNullable(resolveResponseStatus(throwable)).orElse(null);
	}

	private ResponseStatus resolveResponseStatus(final Throwable type) {
		final ResponseStatus candidate = findMergedAnnotation(type.getClass(), ResponseStatus.class);
		return candidate == null && type.getCause() != null ? resolveResponseStatus(type.getCause()) : candidate;
	}

	private URI getMappedType(Throwable err) {
		if (err instanceof MethodArgumentNotValidException) return ErrorConstants.CONSTRAINT_VIOLATION_TYPE;
		if (err instanceof AccessDeniedException) return ErrorConstants.ACCESS_VIOLATION_TYPE;
		if (err instanceof InvalidPluginException || err instanceof InvalidPluginUserUrlException) {
			return ErrorConstants.PLUGIN_VALIDATION_TYPE;
		}
		if (err instanceof InvalidTemplateException) return ErrorConstants.TEMPLATE_VALIDATION_TYPE;
		if (err instanceof DuplicateTagException || err instanceof DuplicateModifiedDateException) {
			return ErrorConstants.DUPLICATE_KEY_TYPE;
		}
		if (err instanceof PublishDateException) return ErrorConstants.DATE_ERROR_TYPE;
		if (err instanceof ScriptException || err instanceof UntrustedScriptException) {
			return ErrorConstants.SCRIPT_ERROR_TYPE;
		}
		if (err instanceof InvalidUserProfileException ||
			err instanceof DeactivateSelfException ||
			err instanceof FreshLoginException) {
			return ErrorConstants.USER_ERROR_TYPE;
		}
		if (err instanceof InvalidTunnelException || err instanceof ScrapeProtocolException) {
			return ErrorConstants.PROTOCOL_ERROR_TYPE;
		}
		return ErrorConstants.DEFAULT_TYPE;
	}

	private String getMappedMessageKey(Throwable err) {
		if (err instanceof MethodArgumentNotValidException) return ErrorConstants.ERR_VALIDATION;
		if (err instanceof ConcurrencyFailureException) return ErrorConstants.ERR_OPTIMISTIC_LOCK;
		if (err instanceof DuplicateTagException) return ErrorConstants.ERR_DUPLICATE_TAG;
		if (err instanceof InvalidPluginException) return ErrorConstants.ERR_INVALID_PLUGIN;
		if (err instanceof InvalidPluginUserUrlException) return ErrorConstants.ERR_INVALID_USER_URL;
		if (err instanceof InvalidTemplateException) return ErrorConstants.ERR_INVALID_TEMPLATE;
		if (err instanceof InvalidPatchException) return ErrorConstants.ERR_INVALID_PATCH;
		if (err instanceof MaxSourcesException) return ErrorConstants.ERR_MAX_SOURCES;
		if (err instanceof NotFoundException) return ErrorConstants.ERR_NOT_FOUND;
		if (err instanceof AccessDeniedException) return ErrorConstants.ERR_ACCESS_DENIED;
		if (err instanceof PublishDateException) return ErrorConstants.ERR_PUBLISH_DATE;
		if (err instanceof ScriptException) return ErrorConstants.ERR_SCRIPT;
		if (err instanceof UntrustedScriptException) return ErrorConstants.ERR_UNTRUSTED_SCRIPT;
		if (err instanceof FreshLoginException) return ErrorConstants.ERR_FRESH_LOGIN;
		if (err instanceof DeactivateSelfException) return ErrorConstants.ERR_DEACTIVATE_SELF;
		if (err instanceof InvalidTunnelException) return ErrorConstants.ERR_INVALID_TUNNEL;
		if (err instanceof ScrapeProtocolException) return ErrorConstants.ERR_SCRAPE_PROTOCOL;
		if (err instanceof OperationForbiddenOnOriginException) return ErrorConstants.ERR_ORIGIN_FORBIDDEN;
		return null;
	}

	private String getCustomizedTitle(Throwable err) {
		if (err instanceof MethodArgumentNotValidException) return "Method argument not valid";
		return null;
	}

	private String getCustomizedErrorDetails(Throwable err) {
		Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
		if (activeProfiles.contains("prod")) {
			if (err instanceof HttpMessageConversionException) return "Unable to convert http message";
			if (err instanceof DataAccessException) return "Failure during data access";
			if (containsPackageName(err.getMessage())) return "Unexpected runtime exception";
		}
		return err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
	}

	private HttpStatus getMappedStatus(Throwable err) {
		// Where we disagree with Spring defaults
		if (err instanceof AccessDeniedException) return HttpStatus.FORBIDDEN;
		if (err instanceof ConcurrencyFailureException) return HttpStatus.CONFLICT;
		if (err instanceof BadCredentialsException) return HttpStatus.UNAUTHORIZED;
		return null;
	}

	private URI getPathValue(NativeWebRequest request) {
		if (request == null) return URI.create("about:blank");
		return URI.create(extractURI(request));
	}

	/**
	 * <p>createFailureAlert.</p>
	 *
	 * @param applicationName a {@link java.lang.String} object.
	 * @param enableTranslation a boolean.
	 * @param entityName a {@link java.lang.String} object.
	 * @param errorKey a {@link java.lang.String} object.
	 * @param defaultMessage a {@link java.lang.String} object.
	 * @return a {@link org.springframework.http.HttpHeaders} object.
	 */
	public static HttpHeaders createFailureAlert(String applicationName, boolean enableTranslation, String entityName, String errorKey, String defaultMessage) {
		String message = enableTranslation ? "error." + errorKey : defaultMessage;

		HttpHeaders headers = new HttpHeaders();
		headers.add("X-" + applicationName + "-error", message);
		headers.add("X-" + applicationName + "-params", entityName);
		return headers;
	}

	private HttpHeaders buildHeaders(Throwable err) {
		return err instanceof BadRequestAlertException badRequestAlertException
			? createFailureAlert(
			"jasper",
			true,
			badRequestAlertException.getEntityName(),
			badRequestAlertException.getErrorKey(),
			badRequestAlertException.getMessage()
		)
			: null;
	}

	public Optional<ProblemDetailWithCause> buildCause(final Throwable throwable, NativeWebRequest request) {
		if (throwable != null && isCasualChainEnabled()) {
			return Optional.of(customizeProblem(getProblemDetailWithCause(throwable), throwable, request));
		}
		return Optional.ofNullable(null);
	}

	private boolean isCasualChainEnabled() {
		// Customize as per the needs
		return CASUAL_CHAIN_ENABLED;
	}

	private boolean containsPackageName(String message) {
		// This list is for sure not complete
		return StringUtils.containsAny(message, "org.", "java.", "net.", "jakarta.", "javax.", "com.", "io.", "de.", "jasper.");
	}
}
