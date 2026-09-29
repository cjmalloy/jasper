package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;

@ResponseStatus(HttpStatus.CONFLICT)
public class PublishDateException extends RuntimeException {

	public PublishDateException(String url, Instant sourcePublished, Instant responsePublished) {
		super("Ref %s must be published after its sources (%s) and before its responses (%s)".formatted(
			url,
			sourcePublished,
			responsePublished));
	}
}
