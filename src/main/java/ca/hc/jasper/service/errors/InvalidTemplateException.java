package ca.hc.jasper.service.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTemplateException extends RuntimeException {

	public InvalidTemplateException(String template) {
		super("Invalid " + template + " template.");
	}
}
