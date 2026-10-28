package jasper.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;

@ResponseStatus(HttpStatus.CONFLICT)
public class PublishDateException extends RuntimeException {

	public PublishDateException(String responseUrl, Instant responsePublished, String sourceUrl, Instant sourcePublished) {
		super("Source %s (%s) must predate response %s (%s)".formatted(
			sourceUrl,
			sourcePublished,
			responseUrl,
			responsePublished));
	}
}
