package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.Ref;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Origin implements Serializable {
	private String local = "";
	private String remote = "";
	private String proxy;

	public static Origin getOrigin(Ref ref) {
		return ref == null ? null : ref.getPlugin("+plugin/origin", Origin.class);
	}
}
