package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.Ref;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


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

	public void migrate(List<Ref> refList, Origin config) {
		for (var ref : refList) {
			migrate(ref, config);
		}
	}

	public void migrate(Ref ref, Origin config) {
		ref.setOrigin(config.getRemote());
		if (isNotBlank(ref.getOrigin()) && ref.getUrl().startsWith("tag:") && !ref.getUrl().endsWith(ref.getOrigin())) {
			ref.setUrl(ref.getUrl() + ref.getOrigin());
		}
	}
}
