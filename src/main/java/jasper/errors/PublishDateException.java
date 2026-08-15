package jasper.errors;

import jasper.domain.Ref;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PublishDateException extends RuntimeException {

	public PublishDateException(Ref response, Ref source) {
		super("Source %s (%s) must predate response %s (%s)".formatted(
			source.getUrl(),
			source.getPublished(),
			response.getUrl(),
			response.getPublished()));
	}
}
