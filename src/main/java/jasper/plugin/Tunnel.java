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
public class Tunnel implements Serializable {
	private String remoteUser;
	private String sshHost;
	private int sshPort = 8022;

	public static Tunnel getTunnel(HasTags ref) {
		return getPlugin(ref, "+plugin/origin/tunnel", Tunnel.class);
	}
}
