package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPluginException extends RuntimeException implements ValidationErrors {

	private final String tag;
	private final String reason;
	private final String summary;
	private final List<FieldError> errors;
	private final boolean truncated;

	public InvalidPluginException(String tag, String reason, String message) {
		this(tag, reason, message, List.of(), null);
	}

	public InvalidPluginException(String tag, String reason, List<FieldError> errors) {
		this(tag, reason, ValidationErrors.summary(errors), errors, null);
	}

	public InvalidPluginException(String tag, String reason, String message, List<FieldError> errors, Throwable cause) {
		this(tag, reason, message, errors, errors != null && errors.size() > MAX_ERRORS, cause);
	}

	private InvalidPluginException(String tag, String reason, String message, List<FieldError> errors, boolean truncated, Throwable cause) {
		super(tag + ": " + message, cause);
		this.tag = tag;
		this.reason = reason;
		this.summary = message;
		this.errors = ValidationErrors.cap(errors);
		this.truncated = truncated;
	}

	/**
	 * Copy with a different reason, e.g. {@link ValidationErrors#INVALID_DEFAULTS}.
	 */
	public InvalidPluginException withReason(String reason) {
		return new InvalidPluginException(tag, reason, summary, errors, truncated, getCause());
	}

	@Override
	public String getTag() {
		return tag;
	}

	@Override
	public String getReason() {
		return reason;
	}

	@Override
	public List<FieldError> getErrors() {
		return errors;
	}

	@Override
	public boolean isTruncated() {
		return truncated;
	}
}
