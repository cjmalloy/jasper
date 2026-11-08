package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;

@Getter
@Setter
@Jacksonized @Builder
@JsonInclude(Include.NON_NULL)
public class Script implements Serializable {
	@Builder.Default
	@JsonSetter(nulls = Nulls.SKIP)
	private int timeoutMs = 30_000;
	@Builder.Default
	@JsonSetter(nulls = Nulls.SKIP)
	private String language = "javascript";
	@Builder.Default
	@JsonSetter(nulls = Nulls.SKIP)
	private String format = "json";
	private String requirements;
	private String script;
}
