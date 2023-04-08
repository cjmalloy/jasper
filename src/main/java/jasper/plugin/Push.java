package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.Ref;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Push {
	private String query;
	private Duration pushInterval;
	private Instant lastPush;
	private int batchSize;
	private boolean checkRemoteCursor;
	private Instant lastModifiedRefWritten;
	private Instant lastModifiedExtWritten;
	private Instant lastModifiedUserWritten;
	private Instant lastModifiedPluginWritten;
	private Instant lastModifiedTemplateWritten;

	public static void migrate(Ref ref) {
		if (isNotBlank(ref.getOrigin()) && ref.getUrl().startsWith("tag:") && !ref.getUrl().endsWith(ref.getOrigin())) {
			ref.setUrl(ref.getUrl() + ref.getOrigin());
		}
	}
}
