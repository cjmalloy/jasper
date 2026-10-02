package jasper.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("gcs")
@Configuration
public class GcsConfig {

	/**
	 * Uses Application Default Credentials, which on GKE resolve
	 * through Workload Identity Federation.
	 */
@Bean
	public Storage gcsClient() {
		return StorageOptions.getDefaultInstance().getService();
	}
}
