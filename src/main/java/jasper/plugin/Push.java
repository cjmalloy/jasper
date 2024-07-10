package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.Ref;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Push implements Serializable {
	private String query;
	private int batchSize;
	private boolean checkRemoteCursor;
	private Instant lastModifiedRefWritten;
	private Instant lastModifiedExtWritten;
	private Instant lastModifiedUserWritten;
	private Instant lastModifiedPluginWritten;
	private Instant lastModifiedTemplateWritten;

	public static Push getPush(Ref ref) {
		return ref == null ? null : ref.getPlugin("+plugin/origin/push", Push.class);
	}
}
