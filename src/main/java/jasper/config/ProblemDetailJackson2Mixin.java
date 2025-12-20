package jasper.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.Map;

/**
 * Jackson 2 mixin for ProblemDetail to ensure dynamic properties are serialized as top-level fields.
 * This is required for HTTP message converters which still use Jackson 2.
 */
public abstract class ProblemDetailJackson2Mixin {
	
	@JsonAnyGetter
	public abstract Map<String, Object> getProperties();
}
