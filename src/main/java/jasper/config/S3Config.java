package jasper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Profile("s3")
@Configuration
public class S3Config {

	/**
	 * Uses the default AWS credentials provider chain (environment variables,
	 * web identity tokens such as EKS IRSA, instance profiles, etc.).
	 * Set an endpoint to use an S3-compatible service such as MinIO or Cloudflare R2.
	 */
	@Bean(destroyMethod = "close")
	public S3Client s3Client(
		@Value("${application.storage.s3.endpoint:}") String endpoint,
		@Value("${application.storage.s3.region:us-east-1}") String region
	) {
		var builder = S3Client.builder()
			.region(Region.of(region));
		if (!endpoint.isBlank()) {
			builder
				.endpointOverride(URI.create(endpoint))
				// S3-compatible services commonly lack virtual-host bucket routing and newer default checksums
				.forcePathStyle(true)
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
		}
		return builder.build();
	}
}
