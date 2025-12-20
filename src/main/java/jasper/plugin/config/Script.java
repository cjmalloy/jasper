package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public record Script(
	int timeoutMs,
	String language,
	String format,
	String requirements,
	String script
) implements Serializable {
	
	// Default constructor with default values
	public Script() {
		this(30_000, "javascript", "json", null, null);
	}
}
