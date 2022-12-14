package jasper.config;

import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private String emailHost = "jasper.local";
	private boolean debug = false;
	private int maxSources = 1000;
	private int backupBufferSize = 1000000;
	private int restoreBatchSize = 500;
	private int maxEtagPageSize = 300;
	private int ingestMaxRetry = 5;
	private String[] modSeals;
	private String[] editorSeals;
	private int asyncBatchSize = 20;
	private int backfillBatchSize = 1000;
	private String asyncDelaySec = "120";
	private String asyncIntervalSec = "40";
	/**
	 * Whitelist origins to run async tasks on.
	 */
	private String[] asyncOrigins = new String[]{""};
	private String scrapeDelayMin = "5";
	private String scrapeIntervalMin = "1";
	/**
	 * Whitelist origins to be allowed to scrape using +plugin/feed.
	 */
	private String[] scrapeOrigins = new String[]{""};
	/**
	 * Whitelist domains to be allowed to scrape.
	 */
	private String[] scrapeHostWhitelist = null;
	/**
	 * Blacklist domains to be allowed to scrape. Takes precedence over domain whitelist.
	 */
	private String[] scrapeHostBlacklist = new String[]{"*.local"};
	private String replicateDelayMin = "5";
	private String replicateIntervalMin = "1";
	/**
	 * Whitelist origins to be allowed to replicate using +plugin/origin.
	 */
	private String[] replicateOrigins = new String[]{""};
	private String sshDelaySec = "120";
	private String sshIntervalSec = "40";
	private String sshConfigNamespace = "default";
	private String sshConfigMapName = "ssh-authorized-keys";
	/**
	 * Whitelist origins to be allowed to open SSH tunnels.
	 */
	private String[] sshOrigins = new String[]{""};
	private int maxReplicateBatch = 5000;
	private String localOrigin = "";
	private boolean allowLocalOriginHeader = false;
	/**
	 * Minimum role for basic access.
	 */
	private String minRole = "ROLE_ANONYMOUS";
	private String storage = "/var/lib/jasper";
	private final Security security = new Security();
	private final ApiDocs apiDocs = new ApiDocs();
	private final CorsConfiguration cors = new CorsConfiguration();


	@Getter
	@Setter
	public static class Security {
		private static final Client defaultClient = new Client();
		private String contentSecurityPolicy;
		private final Map<String, Client> clients = new HashMap<>();

		public static String getOrigin(String client) {
			if (isBlank(client) || client.equals("default")) return "";
			return "@" + client;
		}

		public List<Map.Entry<String, Client>> clientList() {
			return clients.entrySet().stream()
				.map(e -> Map.entry(getOrigin(e.getKey()), e.getValue()))
				.toList();
		}

		public boolean hasClient(String origin) {
			if (origin.equals("") && clients.containsKey("default")) return true;
			return clients.containsKey(origin.substring(1));
		}

		public Client getClient(String origin) {
			if (origin.startsWith("@") && clients.containsKey(origin.substring(1))) {
				return clients.get(origin.substring(1));
			}
			if (origin.equals("") && clients.containsKey("default")) {
				return clients.get("default");
			}
			return defaultClient;
		}

		@Getter
		@Setter
		public static class Client {
			private final ClientAuthorization clientAuthorization = new ClientAuthorization();
			private final Authentication authentication = new Authentication();
			private String scimEndpoint;
			private String usernameClaim = "sub";
			private String authoritiesClaim = "auth";
			private String readAccessClaim = "readAccess";
			private String writeAccessClaim = "writeAccess";
			private String tagReadAccessClaim = "tagReadAccess";
			private String tagWriteAccessClaim = "tagWriteAccess";
			private String defaultRole = "ROLE_ANONYMOUS";
			private String defaultUser = "";
			private String[] defaultReadAccess;
			private String[] defaultWriteAccess;
			private String[] defaultTagReadAccess;
			private String[] defaultTagWriteAccess;
			private boolean allowUsernameClaimOrigin = false;
			private boolean allowUserTagHeader = false;
			private boolean allowUserRoleHeader = false;
			private boolean allowAuthHeaders = false;

			@Getter
			@Setter
			public static class ClientAuthorization {
				private String accessTokenUri;
				private String tokenServiceId;
				private String clientId;
				private String clientSecret;
			}

			@Getter
			@Setter
			public static class Authentication {
				private final Jwt jwt = new Jwt();

				@Getter
				@Setter
				public static class Jwt {
					private String clientId = null;
					private String base64Secret = null;
					private String secret = null;
					private String jwksUri = null;
					private String tokenEndpoint = null;
					private long tokenValidityInSeconds;
					private long tokenValidityInSecondsForRememberMe;

					public String getSecret() {
						if (secret == null) {
							secret = new String(Base64.getDecoder().decode(base64Secret));
						}
						return secret;
					}
				}
			}
		}
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

}
