package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Origin {
	private String local;
	private String remote;
}
