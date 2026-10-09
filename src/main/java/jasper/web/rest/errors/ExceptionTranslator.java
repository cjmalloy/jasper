package jasper.web.rest.errors;

import jakarta.servlet.http.HttpServletRequest;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DeactivateSelfException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.DuplicateTagException;
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
import jasper.errors.ReadOnlyOriginException;
import jasper.errors.RetryableTunnelException;
import jasper.errors.ScrapeProtocolException;
import jasper.errors.ScriptException;
import jasper.errors.TooLargeException;
import jasper.errors.UntrustedScriptException;
import jasper.errors.UserTagInUseException;
import jasper.errors.ValidationErrors;
import jasper.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static jasper.web.rest.errors.ErrorConstants.*;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures.
 * The error response follows RFC 9457 - Problem Details for HTTP APIs (https://www.rfc-editor.org/rfc/rfc9457).
 * Error codes and categories are documented in docs/errors.md.
 */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

	private static final String FIELD_ERRORS_KEY = "fieldErrors";
	private static final String MESSAGE_KEY = "message";
	private static final String PATH_KEY = "path";
	private static final String TAG_KEY = "tag";
	private static final String REASON_KEY = "reason";
	private static final String ERRORS_KEY = "errors";
	private static final String TRUNCATED_KEY = "truncated";
	private static final Pattern PACKAGE_NAME = Pattern.compile("(?<![\\w.])(jasper|org|java|jakarta|javax|com|de|io|net|liquibase)\\.[a-zA-Z_]");

	/**
	 * Ordered lookup table of exception class to error code and category.
	 * The first entry assignable from an exception in the cause chain wins,
	 * so subclasses must be listed before their superclasses.
	 */
	static final Map<Class<? extends Throwable>, ErrorMapping> ERROR_MAPPINGS;
	static {
		var m = new LinkedHashMap<Class<? extends Throwable>, ErrorMapping>();
		// Request
		m.put(MethodArgumentNotValidException.class, new ErrorMapping(ERR_VALIDATION, CONSTRAINT_VIOLATION_TYPE, HttpStatus.BAD_REQUEST));
		m.put(HttpMessageNotReadableException.class, new ErrorMapping(ERR_MESSAGE_NOT_READABLE, REQUEST_ERROR_TYPE, HttpStatus.BAD_REQUEST));
		m.put(MissingServletRequestParameterException.class, new ErrorMapping(ERR_MISSING_PARAMETER, REQUEST_ERROR_TYPE, HttpStatus.BAD_REQUEST));
		m.put(MissingServletRequestPartException.class, new ErrorMapping(ERR_MISSING_PARAMETER, REQUEST_ERROR_TYPE, HttpStatus.BAD_REQUEST));
		m.put(MethodArgumentTypeMismatchException.class, new ErrorMapping(ERR_TYPE_MISMATCH, REQUEST_ERROR_TYPE, HttpStatus.BAD_REQUEST));
		m.put(HttpRequestMethodNotSupportedException.class, new ErrorMapping(ERR_METHOD_NOT_SUPPORTED, REQUEST_ERROR_TYPE, HttpStatus.METHOD_NOT_ALLOWED));
		m.put(HttpMediaTypeNotSupportedException.class, new ErrorMapping(ERR_MEDIA_TYPE_NOT_SUPPORTED, REQUEST_ERROR_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE));
		m.put(InvalidQueryException.class, new ErrorMapping(ERR_INVALID_QUERY, REQUEST_ERROR_TYPE));
		// Constraint
		m.put(InvalidPatchException.class, new ErrorMapping(ERR_INVALID_PATCH, CONSTRAINT_VIOLATION_TYPE));
		m.put(InvalidPushException.class, new ErrorMapping(ERR_INVALID_PUSH, CONSTRAINT_VIOLATION_TYPE));
		// Plugin
		m.put(InvalidPluginException.class, new ErrorMapping(ERR_INVALID_PLUGIN, PLUGIN_VALIDATION_TYPE));
		m.put(InvalidPluginUserUrlException.class, new ErrorMapping(ERR_INVALID_USER_URL, PLUGIN_VALIDATION_TYPE));
		// Template
		m.put(InvalidTemplateException.class, new ErrorMapping(ERR_INVALID_TEMPLATE, TEMPLATE_VALIDATION_TYPE));
		// Access
		m.put(AuthenticationException.class, new ErrorMapping(ERR_UNAUTHORIZED, ACCESS_VIOLATION_TYPE, HttpStatus.UNAUTHORIZED));
		m.put(AccessDeniedException.class, new ErrorMapping(ERR_ACCESS_DENIED, ACCESS_VIOLATION_TYPE, HttpStatus.FORBIDDEN));
		// Missing
		m.put(NotFoundException.class, new ErrorMapping(ERR_NOT_FOUND, NOT_FOUND_TYPE));
		m.put(NoResourceFoundException.class, new ErrorMapping(ERR_NOT_FOUND, NOT_FOUND_TYPE, HttpStatus.NOT_FOUND));
		// Duplicate
		m.put(DuplicateTagException.class, new ErrorMapping(ERR_DUPLICATE_TAG, DUPLICATE_KEY_TYPE));
		m.put(DuplicateModifiedDateException.class, new ErrorMapping(ERR_DUPLICATE_MODIFIED_DATE, DUPLICATE_KEY_TYPE));
		m.put(DuplicateKeyException.class, new ErrorMapping(ERR_DUPLICATE_KEY, DUPLICATE_KEY_TYPE, HttpStatus.CONFLICT));
		// Conflict
		m.put(ConcurrencyFailureException.class, new ErrorMapping(ERR_OPTIMISTIC_LOCK, CONFLICT_TYPE, HttpStatus.CONFLICT));
		m.put(AlreadyExistsException.class, new ErrorMapping(ERR_ALREADY_EXISTS, CONFLICT_TYPE));
		m.put(ModifiedException.class, new ErrorMapping(ERR_MODIFIED, CONFLICT_TYPE));
		m.put(UserTagInUseException.class, new ErrorMapping(ERR_USER_TAG_IN_USE, CONFLICT_TYPE));
		m.put(DataIntegrityViolationException.class, new ErrorMapping(ERR_DATA_INTEGRITY, CONFLICT_TYPE, HttpStatus.CONFLICT));
		// User
		m.put(FreshLoginException.class, new ErrorMapping(ERR_FRESH_LOGIN, USER_ERROR_TYPE));
		m.put(DeactivateSelfException.class, new ErrorMapping(ERR_DEACTIVATE_SELF, USER_ERROR_TYPE));
		m.put(InvalidUserProfileException.class, new ErrorMapping(ERR_INVALID_USER_PROFILE, USER_ERROR_TYPE));
		// Origin
		m.put(OperationForbiddenOnOriginException.class, new ErrorMapping(ERR_ORIGIN_FORBIDDEN, ORIGIN_ERROR_TYPE));
		m.put(PullLocalException.class, new ErrorMapping(ERR_PULL_LOCAL, ORIGIN_ERROR_TYPE));
		m.put(ReadOnlyOriginException.class, new ErrorMapping(ERR_READ_ONLY_ORIGIN, ORIGIN_ERROR_TYPE));
		// Script
		m.put(ScriptException.class, new ErrorMapping(ERR_SCRIPT, SCRIPT_ERROR_TYPE));
		m.put(UntrustedScriptException.class, new ErrorMapping(ERR_UNTRUSTED_SCRIPT, SCRIPT_ERROR_TYPE));
		// Date
		m.put(PublishDateException.class, new ErrorMapping(ERR_PUBLISH_DATE, DATE_ERROR_TYPE));
		// Protocol
		m.put(InvalidTunnelException.class, new ErrorMapping(ERR_INVALID_TUNNEL, PROTOCOL_ERROR_TYPE));
		m.put(RetryableTunnelException.class, new ErrorMapping(ERR_TUNNEL_TIMEOUT, PROTOCOL_ERROR_TYPE));
		m.put(ScrapeProtocolException.class, new ErrorMapping(ERR_SCRAPE_PROTOCOL, PROTOCOL_ERROR_TYPE));
		// Size
		m.put(TooLargeException.class, new ErrorMapping(ERR_TOO_LARGE, SIZE_ERROR_TYPE));
		m.put(MaxUploadSizeExceededException.class, new ErrorMapping(ERR_TOO_LARGE, SIZE_ERROR_TYPE, HttpStatus.PAYLOAD_TOO_LARGE));
		m.put(MaxSourcesException.class, new ErrorMapping(ERR_MAX_SOURCES, SIZE_ERROR_TYPE));
		// Unavailable
		m.put(NotAvailableException.class, new ErrorMapping(ERR_NOT_AVAILABLE, UNAVAILABLE_TYPE));
		ERROR_MAPPINGS = Collections.unmodifiableMap(m);
	}

	/**
	 * Find the mapping for the first exception in the cause chain that has one.
	 */
	static Optional<ErrorMapping> findMapping(Throwable err) {
		var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
		for (var t = err; t != null && seen.add(t); t = t.getCause()) {
			var mapping = mappingFor(t.getClass());
			if (mapping.isPresent()) return mapping;
		}
		return Optional.empty();
	}

	static Optional<ErrorMapping> mappingFor(Class<?> type) {
		for (var e : ERROR_MAPPINGS.entrySet()) {
			if (e.getKey().isAssignableFrom(type)) return Optional.of(e.getValue());
		}
		return Optional.empty();
	}

	private static <T> Optional<T> findCause(Throwable err, Class<T> type) {
		var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
		for (var t = err; t != null && seen.add(t); t = t.getCause()) {
			if (type.isInstance(t)) return Optional.of(type.cast(t));
		}
		return Optional.empty();
	}

	private static boolean causedBy(Throwable err, Class<?> type) {
		return findCause(err, type).isPresent();
	}

	private final Environment env;

	public ExceptionTranslator(Environment env) {
		this.env = env;
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleAnyException(Throwable ex, NativeWebRequest request) {
		ProblemDetailWithCause pdCause = wrapAndCustomizeProblem(ex, request);
		return handleExceptionInternal((Exception) ex, pdCause, new HttpHeaders(), HttpStatusCode.valueOf(pdCause.getStatus()), request);
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
		// Replace null bodies and plain Spring ProblemDetail bodies so every response has a code and category
		if (!(body instanceof ProblemDetailWithCause)) body = wrapAndCustomizeProblem(ex, (NativeWebRequest) request);
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

		var mapping = findMapping(err);
		if (problem.getType() == null || problem.getType().equals(URI.create("about:blank"))) {
			problem.setType(mapping.map(ErrorMapping::type).orElse(DEFAULT_TYPE));
		}

		// higher precedence to Custom/ResponseStatus types
		String title = extractTitle(err, problem.getStatus());
		String problemTitle = problem.getTitle();
		if (problemTitle == null || !problemTitle.equals(title)) {
			problem.setTitle(title);
		}

		if (problem.getDetail() == null) {
			// higher precedence to cause
			problem.setDetail(getCustomizedErrorDetails(err, problem.getStatus()));
		}

		Map<String, Object> problemProperties = problem.getProperties();
		problem.setProperty(
			MESSAGE_KEY,
			mapping.map(ErrorMapping::code).orElse("error.http." + problem.getStatus())
		);

		if (problemProperties == null || !problemProperties.containsKey(PATH_KEY)) problem.setProperty(PATH_KEY, getPathValue(request));

		if (
			(err instanceof MethodArgumentNotValidException fieldException) &&
				(problemProperties == null || !problemProperties.containsKey(FIELD_ERRORS_KEY))
		) problem.setProperty(FIELD_ERRORS_KEY, getFieldErrors(fieldException));

		findCause(err, ValidationErrors.class).ifPresent(v -> {
			problem.setProperty(TAG_KEY, v.getTag());
			problem.setProperty(REASON_KEY, v.getReason());
			problem.setProperty(ERRORS_KEY, v.getErrors());
			problem.setProperty(TRUNCATED_KEY, v.isTruncated());
		});

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
		return specialStatus == null || StringUtils.isBlank(specialStatus.reason())
			? HttpStatus.valueOf(statusCode).getReasonPhrase()
			: specialStatus.reason();
	}

	private String extractURI(NativeWebRequest request) {
		HttpServletRequest nativeRequest = request.getNativeRequest(HttpServletRequest.class);
		return nativeRequest != null ? nativeRequest.getRequestURI() : StringUtils.EMPTY;
	}

	private HttpStatus toStatus(final Throwable throwable) {
		// Let the ErrorResponse take this responsibility
		if (throwable instanceof ErrorResponse err) return HttpStatus.valueOf(err.getBody().getStatus());

		return findMapping(throwable)
			.map(ErrorMapping::status)
			.or(() -> Optional.ofNullable(resolveResponseStatus(throwable)).map(ResponseStatus::value))
			.orElse(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private ResponseStatus extractResponseStatus(final Throwable throwable) {
		return Optional.ofNullable(resolveResponseStatus(throwable)).orElse(null);
	}

	private ResponseStatus resolveResponseStatus(final Throwable type) {
		final ResponseStatus candidate = findMergedAnnotation(type.getClass(), ResponseStatus.class);
		return candidate == null && type.getCause() != null ? resolveResponseStatus(type.getCause()) : candidate;
	}

	private String getCustomizedTitle(Throwable err) {
		if (err instanceof MethodArgumentNotValidException) return "Method argument not valid";
		return null;
	}

	private String getCustomizedErrorDetails(Throwable err, int status) {
		// Plugin and template validation summaries are user-facing and never contain submitted values
		var validation = findCause(err, ValidationErrors.class);
		if (validation.isPresent()) return ((Throwable) validation.get()).getMessage();
		var detail = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
		if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
			if (causedBy(err, HttpMessageConversionException.class)) return "Unable to convert http message";
			if (causedBy(err, DataAccessException.class)) return "Failure during data access";
			if (status >= 500) return "Unexpected runtime exception";
			if (containsPackageName(detail)) return "Unexpected runtime exception";
		}
		return detail;
	}

	private URI getPathValue(NativeWebRequest request) {
		if (request == null) return URI.create("about:blank");
		return URI.create(extractURI(request));
	}

	private boolean containsPackageName(String message) {
		return message != null && PACKAGE_NAME.matcher(message).find();
	}
}
