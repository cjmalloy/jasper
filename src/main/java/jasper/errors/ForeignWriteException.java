package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ForeignWriteException extends RuntimeException {
	public ForeignWriteException(String origin) {
		super("Writing to origin " + origin + " unauthorized");
	}
}
