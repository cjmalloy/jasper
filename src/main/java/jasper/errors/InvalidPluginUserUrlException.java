package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPluginUserUrlException extends RuntimeException implements ValidationErrors {

	private final String tag;
	private final String reason;

	public InvalidPluginUserUrlException(String tag, String reason, String message) {
		super(tag + ": " + message);
		this.tag = tag;
		this.reason = reason;
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
		return List.of();
	}

	@Override
	public boolean isTruncated() {
		return false;
	}
}
