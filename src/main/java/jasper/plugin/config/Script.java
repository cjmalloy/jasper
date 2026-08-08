package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Jacksonized @Builder
@JsonInclude(Include.NON_NULL)
public class Script implements Serializable {
	@Builder.Default
	private int timeoutMs = 30_000;
	@Builder.Default
	private String language = "javascript";
	@Builder.Default
	private String format = "json";
	@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
	private List<String> requirements;
	private String script;
}
