package ca.hc.jasper.service.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPatchException extends RuntimeException {

	public InvalidPatchException(Throwable cause) {
		super(cause);
	}
}
