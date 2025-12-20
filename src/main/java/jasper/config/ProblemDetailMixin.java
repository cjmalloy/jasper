package jasper.config;

import tools.jackson.annotation.JsonAnyGetter;
import java.util.Map;

/**
 * Jackson mixin for ProblemDetail to ensure dynamic properties are serialized as top-level fields.
 * This is required for Jackson 3 to properly serialize properties set via setProperty().
 */
public abstract class ProblemDetailMixin {
	
	@JsonAnyGetter
	public abstract Map<String, Object> getProperties();
}
