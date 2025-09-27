package jasper.config;

import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static jasper.domain.proj.HasOrigin.subOrigin;
import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
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
	 * _config/server/sub-origin file in the local origin to perform certain tasks.
	 * The sub origin for this worker is at the index in the worker name.
	 */
	private String[] workload;
	/**
	 * The name of this worker. The number suffix is used as an index
	 * into workload to determine the worker sub origin.
	 */
	private String worker;
	/**
	 * Worker sub-origin.
	 */
	public String getWorkerOrigin() {
		if (isNotEmpty(workload) && isNotBlank(worker)) {
			var index = worker.replaceAll("\\D", "");
			return workload[isBlank(index) ? 0 : parseInt(index)];
		} else {
			return "";
		}
	}
	/**
	 * Local origin for this server.
	 */
	private String localOrigin = "";
	/**
	 * Computed origin (local + worker).
	 */
	public String getOrigin() {
		return subOrigin(getLocalOrigin(), getWorkerOrigin());
	}
	private int ingestMaxRetry = 5;
	private int maxEtagPageSize = 300;
	private int backupBufferSize = 1000000;
	private int restoreBatchSize = 500;
	private int backfillBatchSize = 100;
	private String pullDelayMin = "5";
	private String pullIntervalMin = "1";
	private String scrapeDelayMin = "5";
	private String scrapeIntervalMin = "1";
	private int clearCacheCooldownSec = 2;
	private int pushCooldownSec = 1;
	private int pullWebsocketCooldownSec = 10;
	private boolean allowUserTagHeader = false;
	private boolean allowUserRoleHeader = false;
	private boolean allowAuthHeaders = false;
	/**
	 * Highest role allowed access.
	 */
	private String maxRole = "ROLE_ADMIN";
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
	private String shell = "/usr/bin/bash";
	private String cacheApi = "";

	private String sshConfigNamespace = "default";
	private String sshConfigMapName = "ssh-authorized-keys";
	private String sshSecretName = "ssh-host-key";

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
		private Integer maxReplEntityBatch;
		private List<String> sshOrigins;
		private Integer maxPushEntityBatch;
		private Integer maxPullEntityBatch;
		private List<String> scriptSelectors;
		private List<String> scriptWhitelist;
		private List<String> hostWhitelist;
		private List<String> hostBlacklist;
		private Integer maxConcurrentRequestsPerOrigin;
		private Integer maxConcurrentScriptsPerOrigin;
		private Integer maxConcurrentCronScriptsPerOrigin;
		private Integer maxConcurrentReplicationPerOrigin;
		private Integer maxConcurrentRssScrapePerOrigin;
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
			private int timeToLiveInDays = 1461; // 4 years (including leap day)
		}
	}
	@Getter
	@Setter
	public static class Mail {
		private boolean enabled = false;
		private String from = "";
		private String baseUrl = "";
	}

	@Getter
	@Setter
	public static class Security {
		private String contentSecurityPolicy = "default-src 'self'; frame-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:";
	}

	@Getter
	@Setter
	public static class ApiDocs {
		private String title = "";
		private String description = "";
		private String version = "";
		private String termsOfServiceUrl = "";
		private String contactName = "";
		private String contactUrl = "";
		private String contactEmail = "";
		private License license;
		private String defaultIncludePattern = "";
		private String managementIncludePattern = "";
		private List<Server> servers;
	}

	@Getter
	@Setter
	public static class AuditEvents {
		private int retentionPeriod = 30;
	}
}
