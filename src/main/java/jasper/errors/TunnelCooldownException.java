package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
public class TunnelCooldownException extends RetryableTunnelException {

	public TunnelCooldownException(String msg) {
		super(msg);
	}
}
