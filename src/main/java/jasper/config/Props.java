package jasper.config;

import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;
import tech.jhipster.config.JHipsterDefaults;

import java.util.List;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
	/**
	 * List of workers to create by origin. Each worker will use the
	 * _config/server file in it's origin to perform certain tasks.
	 * The local origin for this worker is at the index in the worker name.
	 */
	private String[] workload;
	/**
	 * The name of this worker. The number suffix is used as an index
	 * into workload to determine the local origin.
	 */
	private String worker;
	/**
	 * Override the worker origin;
	 */
	private String workerLocalOrigin;
	/**
	 * Local origin for this server.
	 * Only used if worker name is not set.
	 */
	private String localOrigin = "";
	public String getLocalOrigin() {
		if (isNotBlank(workerLocalOrigin)) {
			return workerLocalOrigin;
		} else if (isNotBlank(worker)) {
			return workerLocalOrigin = workload[parseInt(worker.replaceAll("\\D", ""))];
		} else {
			return localOrigin;
		}
	}
	private int ingestMaxRetry = 5;
	private int maxEtagPageSize = 300;
	private int backupBufferSize = 1000000;
	private int restoreBatchSize = 500;
	private int backfillBatchSize = 1000;
	private String pullDelayMin = "5";
	private String pullIntervalMin = "1";
	private String scrapeDelayMin = "5";
	private String scrapeIntervalMin = "1";
	private int clearCacheCooldownSec = 2;
	private int pushCooldownSec = 1;
	private boolean allowLocalOriginHeader = false;
	private boolean allowUserTagHeader = false;
	private boolean allowUserRoleHeader = false;
	private boolean allowAuthHeaders = false;
	/**
	 * Minimum role for basic access.
	 */
	private String minRole = "ROLE_ANONYMOUS";
	/**
	 * Minimum role for writing.
	 */
	private String minWriteRole = "ROLE_VIEWER";
	/**
	 * Minimum role for admin config.
	 */
	private String minConfigRole = "ROLE_ADMIN";
	/**
	 * Minimum role for downloading backups.
	 * Backups may contain private data or private SSH keys, so they are extremely sensitive.
	 */
	private String minReadBackupsRole = "ROLE_ADMIN";
	/**
	 * Default role given to every user.
	 */
	private String defaultRole = "ROLE_ANONYMOUS";
	private String[] defaultReadAccess;
	private String[] defaultWriteAccess;
	private String[] defaultTagReadAccess;
	private String[] defaultTagWriteAccess;

	private String storage = "/var/lib/jasper";
	private String node = "/usr/local/bin/node";
	private String python = "/usr/bin/python";
	private String cacheApi = "";

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
		 * Override any server settings for all origins.
		 */
		private final ServerOverrides server = new ServerOverrides();
		/**
		 * Override any security settings for all origins.
		 */
		private final SecurityOverrides security = new SecurityOverrides();
	}

	@Getter
	@Setter
	public static class ServerOverrides {
		private String emailHost;
		private Integer maxSources;
		private List<String> modSeals;
		private List<String> editorSeals;
		private List<String> webOrigins;
		private List<String> sshOrigins;
		private List<String> pushOrigins;
		private Integer maxPushEntityBatch;
		private List<String> pullOrigins;
		private Integer maxPullEntityBatch;
		private List<String> scriptOrigins;
		private List<String> scrapeHostWhitelist;
		private List<String> scrapeHostBlacklist;
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
		private String verifiedEmailClaim = "unset";
		private String defaultUser = "";
		private String tokenEndpoint = "";
		private String scimEndpoint = "";
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
