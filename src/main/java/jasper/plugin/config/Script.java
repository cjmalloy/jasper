package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;

import java.io.Serializable;

@Builder
@JsonDeserialize(builder = Script.ScriptBuilder.class)
@JsonInclude(Include.NON_NULL)
public record Script(
	int timeoutMs,
	String language,
	String format,
	String requirements,
	String script
) implements Serializable {
	
	@JsonPOJOBuilder(withPrefix = "")
	public static class ScriptBuilder {
		// Lombok will generate this class
		// Initialize defaults
		private int timeoutMs = 30_000;
		private String language = "javascript";
		private String format = "json";
	}
}
