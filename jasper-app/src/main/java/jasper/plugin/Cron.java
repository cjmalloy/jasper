package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.Ref;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Duration;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Cron implements Serializable {
	private Duration interval;

	public static Cron getCron(Ref ref) {
		return ref == null ? null : ref.getPlugin("+plugin/cron", Cron.class);
	}
}
