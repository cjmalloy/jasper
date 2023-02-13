package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Push {
	private String query;
	private String proxy;
	private Duration pushInterval;
	private Instant lastPush;
	private int batchSize;
	private boolean writeOnly = false;
	private Instant lastModifiedRefWritten;
	private Instant lastModifiedExtWritten;
	private Instant lastModifiedUserWritten;
	private Instant lastModifiedPluginWritten;
	private Instant lastModifiedTemplateWritten;
}
