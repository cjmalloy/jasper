package jasper.web.rest.errors;

import java.net.URI;

/**
 * Stable error codes (returned in {@code $.message}) and category URIs (returned in {@code $.type}).
 * Every category is documented under a matching heading in docs/errors.md.
 */
public final class ErrorConstants {

	// Error codes
	public static final String ERR_VALIDATION = "error.validation";
	public static final String ERR_MESSAGE_NOT_READABLE = "error.messageNotReadable";
	public static final String ERR_MISSING_PARAMETER = "error.missingParameter";
	public static final String ERR_TYPE_MISMATCH = "error.typeMismatch";
	public static final String ERR_METHOD_NOT_SUPPORTED = "error.methodNotSupported";
	public static final String ERR_MEDIA_TYPE_NOT_SUPPORTED = "error.mediaTypeNotSupported";
	public static final String ERR_INVALID_QUERY = "error.invalidQuery";
	public static final String ERR_INVALID_PATCH = "error.invalidPatch";
	public static final String ERR_INVALID_PUSH = "error.invalidPush";
	public static final String ERR_INVALID_PLUGIN = "error.invalidPlugin";
	public static final String ERR_INVALID_USER_URL = "error.invalidUserUrl";
	public static final String ERR_INVALID_TEMPLATE = "error.invalidTemplate";
	public static final String ERR_UNAUTHORIZED = "error.unauthorized";
	public static final String ERR_ACCESS_DENIED = "error.accessDenied";
	public static final String ERR_NOT_FOUND = "error.notFound";
	public static final String ERR_DUPLICATE_TAG = "error.duplicateTag";
	public static final String ERR_DUPLICATE_MODIFIED_DATE = "error.duplicateModifiedDate";
	public static final String ERR_DUPLICATE_KEY = "error.duplicateKey";
	public static final String ERR_OPTIMISTIC_LOCK = "error.optimisticLock";
	public static final String ERR_ALREADY_EXISTS = "error.alreadyExists";
	public static final String ERR_MODIFIED = "error.modified";
	public static final String ERR_USER_TAG_IN_USE = "error.userTagInUse";
	public static final String ERR_DATA_INTEGRITY = "error.dataIntegrity";
	public static final String ERR_FRESH_LOGIN = "error.freshLogin";
	public static final String ERR_DEACTIVATE_SELF = "error.deactivateSelf";
	public static final String ERR_INVALID_USER_PROFILE = "error.invalidUserProfile";
	public static final String ERR_ORIGIN_FORBIDDEN = "error.originForbidden";
	public static final String ERR_PULL_LOCAL = "error.pullLocal";
	public static final String ERR_READ_ONLY_ORIGIN = "error.readOnlyOrigin";
	public static final String ERR_SCRIPT = "error.script";
	public static final String ERR_UNTRUSTED_SCRIPT = "error.untrustedScript";
	public static final String ERR_PUBLISH_DATE = "error.publishDate";
	public static final String ERR_INVALID_TUNNEL = "error.invalidTunnel";
	public static final String ERR_TUNNEL_TIMEOUT = "error.tunnelTimeout";
	public static final String ERR_SCRAPE_PROTOCOL = "error.scrapeProtocol";
	public static final String ERR_TOO_LARGE = "error.tooLarge";
	public static final String ERR_MAX_SOURCES = "error.maxSources";
	public static final String ERR_NOT_AVAILABLE = "error.notAvailable";

	// Problem URLs
	public static final String PROBLEM_BASE_URL = "https://cjmalloy.github.io/jasper/docs/errors.html";
	public static final URI DEFAULT_TYPE = URI.create(PROBLEM_BASE_URL + "#general");
	public static final URI REQUEST_ERROR_TYPE = URI.create(PROBLEM_BASE_URL + "#request");
	public static final URI CONSTRAINT_VIOLATION_TYPE = URI.create(PROBLEM_BASE_URL + "#constraint");
	public static final URI PLUGIN_VALIDATION_TYPE = URI.create(PROBLEM_BASE_URL + "#plugin");
	public static final URI TEMPLATE_VALIDATION_TYPE = URI.create(PROBLEM_BASE_URL + "#template");
	public static final URI ACCESS_VIOLATION_TYPE = URI.create(PROBLEM_BASE_URL + "#access");
	public static final URI NOT_FOUND_TYPE = URI.create(PROBLEM_BASE_URL + "#missing");
	public static final URI DUPLICATE_KEY_TYPE = URI.create(PROBLEM_BASE_URL + "#duplicate");
	public static final URI CONFLICT_TYPE = URI.create(PROBLEM_BASE_URL + "#conflict");
	public static final URI USER_ERROR_TYPE = URI.create(PROBLEM_BASE_URL + "#user");
	public static final URI ORIGIN_ERROR_TYPE = URI.create(PROBLEM_BASE_URL + "#origin");
	public static final URI SCRIPT_ERROR_TYPE = URI.create(PROBLEM_BASE_URL + "#script");
	public static final URI DATE_ERROR_TYPE = URI.create(PROBLEM_BASE_URL + "#date");
	public static final URI PROTOCOL_ERROR_TYPE = URI.create(PROBLEM_BASE_URL + "#protocol");
	public static final URI SIZE_ERROR_TYPE = URI.create(PROBLEM_BASE_URL + "#size");
	public static final URI UNAVAILABLE_TYPE = URI.create(PROBLEM_BASE_URL + "#unavailable");

	private ErrorConstants() {}
}
