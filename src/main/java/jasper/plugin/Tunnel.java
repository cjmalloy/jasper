package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Tunnel implements Serializable {
	private String remoteUser;
	private String sshHost;
	private int sshPort = 8022;
}
