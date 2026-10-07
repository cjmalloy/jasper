package jasper.errors;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Structured details for plugin and template validation failures.
 */
public interface ValidationErrors {
	/**
	 * Maximum number of {@link FieldError}s returned.
	 */
	int MAX_ERRORS = 50;
	int SUMMARY_ERRORS = 5;

	String SCHEMA = "schema";
	String UNTAGGED = "untagged";
	String SCHEMALESS = "schemaless";
	String MAX_DEPTH = "maxDepth";
	String USER_URL_SOURCES = "userUrl.sources";
	String USER_URL_USER_TAG = "userUrl.userTag";
	String USER_URL_PREFIX = "userUrl.prefix";
	String CONFIG = "config";
	String INVALID_DEFAULTS = "invalidDefaults";

	String getTag();
	String getReason();
	List<FieldError> getErrors();
	boolean isTruncated();

	static List<FieldError> cap(List<FieldError> errors) {
		if (errors == null) return List.of();
		return List.copyOf(errors.size() > MAX_ERRORS ? errors.subList(0, MAX_ERRORS) : errors);
	}

	/**
	 * One line summary of field errors, e.g. {@code age: expected uint32; name: required}.
	 */
	static String summary(List<FieldError> errors) {
		var result = errors.stream()
			.limit(SUMMARY_ERRORS)
			.map(FieldError::message)
			.collect(Collectors.joining("; "));
		if (errors.size() > SUMMARY_ERRORS) result += "; and " + (errors.size() - SUMMARY_ERRORS) + " more";
		return result;
	}
}
