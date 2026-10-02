package jasper.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Local settings for Google Cloud Storage, bound to {@code application.storage.gcs}.
 * Buckets and routes are set in the server config with {@link Config.ServerConfig#getGcsBucket()}
 * and {@link Config.ServerConfig#getGcsRoutes()}.
 */
@Getter
@Setter
public class GcsProps {
	/**
	 * Folder used to stage zip archives.
	 */
	private String tmpDir = System.getProperty("java.io.tmpdir");
}
