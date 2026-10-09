package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ReadOnlyOriginException extends RuntimeException {
	public ReadOnlyOriginException(String origin) {
		super("Origin (" + origin + ") is pulled from a remote and is read only. Only deletes are allowed.");
	}
}
