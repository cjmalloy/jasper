package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class UserManagerException extends RuntimeException {
	public UserManagerException(Exception e) {
		super(e);
	}
}
