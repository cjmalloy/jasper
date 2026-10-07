package jasper.web.rest.errors;

import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Error code and category for an exception class.
 *
 * @param code   stable error code returned in {@code $.message}
 * @param type   category URI returned in {@code $.type}
 * @param status HTTP status, or null to use the exception's {@code @ResponseStatus}
 */
public record ErrorMapping(String code, URI type, HttpStatus status) {
	public ErrorMapping(String code, URI type) {
		this(code, type, null);
	}
}
