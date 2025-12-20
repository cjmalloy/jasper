package jasper.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.Map;

/**
 * Jackson 2 mixin for ProblemDetail to ensure dynamic properties are serialized as top-level fields.
 * This is required for Jackson 2 (used by Spring Boot's HTTP message converters) to properly serialize
 * properties set via setProperty().
 */
public abstract class ProblemDetailJackson2Mixin {
	
	@JsonAnyGetter
	public abstract Map<String, Object> getProperties();
}
