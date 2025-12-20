package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class Script implements Serializable {
	@Builder.Default
	private int timeoutMs = 30_000;
	@Builder.Default
	private String language = "javascript";
	@Builder.Default
	private String format = "json";
	private String requirements;
	private String script;
}
