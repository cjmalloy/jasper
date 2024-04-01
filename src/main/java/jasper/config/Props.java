package jasper.config;

import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;
import tech.jhipster.config.JHipsterDefaults;

import java.util.Base64;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Properties specific to Jasper.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jasper", ignoreUnknownFields = false)
public class Props {
	private boolean debug = false;
	private int ingestMaxRetry = 5;
	private int maxEtagPageSize = 300;
	private int backupBufferSize = 1000000;
	private int restoreBatchSize = 500;
	private int backfillBatchSize = 1000;
	private String scrapeDelayMin = "5";
	private String scrapeIntervalMin = "1";
	private int pushCooldownSec = 1;
	private String pullDelayMin = "5";
	private String pullIntervalMin = "1";
	private String localOrigin = "";
	private boolean allowLocalOriginHeader = false;
	private boolean allowUserTagHeader = false;
	private boolean allowUserRoleHeader = false;
	private boolean allowAuthHeaders = false;
	/**
	 * Minimum role for basic access.
	 */
	private String minRole = "ROLE_ANONYMOUS";
	/**
	 * Default role given to every user.
	 */
	private String defaultRole = "ROLE_ANONYMOUS";
	private String storage = "/var/lib/jasper";
	private String[] defaultReadAccess;
	private String[] defaultWriteAccess;
	private String[] defaultTagReadAccess;
	private String[] defaultTagWriteAccess;

	private String sshConfigNamespace = "default";
	private String sshConfigMapName = "ssh-authorized-keys";

	private final Overrides override = new Overrides();
	private final Http http = new Http();
	private final Mail mail = new Mail();
	private final Security security = new Security();
	private final ApiDocs apiDocs = new ApiDocs();
	private final CorsConfiguration cors = new CorsConfiguration();
	private final AuditEvents auditEvents = new AuditEvents();

	@Getter
	@Setter
	public static class Overrides {
		/**
		 * Override any security settings for all origins.
		 */
		private final SecurityOverrides security = new SecurityOverrides();
	}

	@Getter
	@Setter
	public static class SecurityOverrides {
		private String mode = "";
		private String clientId = "";
		private String base64Secret = "";
		private String secret = "";
		private String jwksUri = "";
		private String usernameClaim = "";
		private String tokenEndpoint = "";
		private String scimEndpoint = "";

		public String getSecret() {
			if (isBlank(secret)) {
				secret = new String(Base64.getDecoder().decode(base64Secret));
			}
			return secret;
		}
	}


	@Getter
	@Setter
	public static class Http {
		private final Cache cache = new Cache();

		@Getter
		@Setter
		public static class Cache {
			private int timeToLiveInDays = JHipsterDefaults.Http.Cache.timeToLiveInDays;
		}
	}
	@Getter
	@Setter
	public static class Mail {
		private boolean enabled = JHipsterDefaults.Mail.enabled;
		private String from = JHipsterDefaults.Mail.from;
		private String baseUrl = JHipsterDefaults.Mail.baseUrl;
	}

	@Getter
	@Setter
	public static class Security {
		private String contentSecurityPolicy = JHipsterDefaults.Security.contentSecurityPolicy;
	}

	@Getter
	@Setter
	public static class ApiDocs {
		private String title = JHipsterDefaults.ApiDocs.title;
		private String description = JHipsterDefaults.ApiDocs.description;
		private String version = JHipsterDefaults.ApiDocs.version;
		private String termsOfServiceUrl = JHipsterDefaults.ApiDocs.termsOfServiceUrl;
		private String contactName = JHipsterDefaults.ApiDocs.contactName;
		private String contactUrl = JHipsterDefaults.ApiDocs.contactUrl;
		private String contactEmail = JHipsterDefaults.ApiDocs.contactEmail;
		private License license;
		private String defaultIncludePattern = JHipsterDefaults.ApiDocs.defaultIncludePattern;
		private String managementIncludePattern = JHipsterDefaults.ApiDocs.managementIncludePattern;
		private List<Server> servers;
	}

	@Getter
	@Setter
	public static class AuditEvents {
		private int retentionPeriod = JHipsterDefaults.AuditEvents.retentionPeriod;
	}
}
