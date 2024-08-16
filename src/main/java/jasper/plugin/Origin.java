package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.proj.HasTags;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

import static jasper.domain.proj.HasTags.getPlugin;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Origin implements Serializable {
	private String local = "";
	private String remote = "";
	private String proxy;

	public static Origin getOrigin(HasTags ref) {
		return getPlugin(ref, "+plugin/origin", Origin.class);
	}
}
