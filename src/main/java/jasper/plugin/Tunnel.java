package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import jasper.domain.proj.HasTags;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

import static jasper.domain.proj.HasTags.getPlugin;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Tunnel implements Serializable {
	private String hostFingerprint;
	private String remoteUser;
	private String sshHost;
	private int sshPort = 8022;
	/**
	 * How to transfer cache files over SFTP instead of HTTP.
	 * Requires read-only storage access on the remote jasper-ssh server.
	 * Defaults to off.
	 */
	private SftpMode sftp;

	public enum SftpMode {
		/**
		 * Transfer cache files over HTTP.
		 */
		@JsonProperty("off") OFF,
		/**
		 * Stream cache files from SFTP every time, without storing them in the local cache.
		 */
		@JsonProperty("stream") STREAM,
		/**
		 * Stream cache files from SFTP if missing from the local cache, and store them in the local cache.
		 */
		@JsonProperty("cache") CACHE,
		/**
		 * Constantly sync new cache files from SFTP into the local cache, like rclone.
		 * Cache files that have not been synced yet are fetched as in {@link #CACHE} mode.
		 */
		@JsonProperty("sync") SYNC,
	}

	private static final Tunnel DEFAULTS = new Tunnel();
	public static Tunnel getTunnel(HasTags ref) {
		var tunnel = ref == null ? null : getPlugin(ref, "+plugin/origin/tunnel", Tunnel.class);
		return tunnel == null ? DEFAULTS : tunnel;
	}
}
