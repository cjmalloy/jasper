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
	private boolean pushOnChange;
	private String query;
	private int batchSize;
	private boolean checkRemoteCursor;
	private Instant lastModifiedRefWritten;
	private Instant lastModifiedExtWritten;
	private Instant lastModifiedUserWritten;
	private Instant lastModifiedPluginWritten;
	private Instant lastModifiedTemplateWritten;

	private static final Push DEFAULTS = new Push();
	public static Push getPush(Ref ref) {
		var push = ref == null ? null : ref.getPlugin("+plugin/origin/push", Push.class);
		return push == null ? DEFAULTS : push;
	}
}
