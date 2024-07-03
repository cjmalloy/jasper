package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Delta implements Serializable {
	private String language = "javascript";
	private String script;
	private int timeoutMs;
}
