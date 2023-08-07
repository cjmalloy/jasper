package jasper.config;

import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;
import tech.jhipster.config.JHipsterDefaults;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
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
	private int ingestMaxRetry = 5;
	private String[] modSeals;
	private String[] editorSeals;
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
	private int maxReplicateBatch = 5000;
	private String localOrigin = "";
	private boolean allowLocalOriginHeader = false;
	private boolean multiTenant = false;
	/**
	 * Minimum role for basic access.
	 */
	private String minRole = "ROLE_ANONYMOUS";
	private String storage = "/var/lib/jasper";
	private final Async async = new Async();
	private final Http http = new Http();
	private final Database database = new Database();
	private final Cache cache = new Cache();
	private final Mail mail = new Mail();
	private final Security security = new Security();
	private final ApiDocs apiDocs = new ApiDocs();
	private final Logging logging = new Logging();
	private final CorsConfiguration cors = new CorsConfiguration();
	private final Social social = new Social();
	private final Gateway gateway = new Gateway();
	private final Registry registry = new Registry();
	private final ClientApp clientApp = new ClientApp();
	private final AuditEvents auditEvents = new AuditEvents();

	@Getter
	@Setter
	public static class Async {
		private int corePoolSize = JHipsterDefaults.Async.corePoolSize;
		private int maxPoolSize = JHipsterDefaults.Async.maxPoolSize;
		private int queueCapacity = JHipsterDefaults.Async.queueCapacity;
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
	public static class Database {
		private final Couchbase couchbase = new Couchbase();

		@Getter
		@Setter
		public static class Couchbase {
			private String bucketName;
			private String scopeName;
		}
	}

	@Getter
	@Setter
	public static class Cache {
		private final Hazelcast hazelcast = new Hazelcast();
		private final Caffeine caffeine = new Caffeine();
		private final Ehcache ehcache = new Ehcache();
		private final Infinispan infinispan = new Infinispan();
		private final Memcached memcached = new Memcached();
		private final Redis redis = new Redis();

		@Getter
		@Setter
		public static class Hazelcast {
			private int timeToLiveSeconds = JHipsterDefaults.Cache.Hazelcast.timeToLiveSeconds;
			private int backupCount = JHipsterDefaults.Cache.Hazelcast.backupCount;
		}

		@Getter
		@Setter
		public static class Caffeine {
			private int timeToLiveSeconds = JHipsterDefaults.Cache.Caffeine.timeToLiveSeconds;
			private long maxEntries = JHipsterDefaults.Cache.Caffeine.maxEntries;
		}

		@Getter
		@Setter
		public static class Ehcache {
			private int timeToLiveSeconds = JHipsterDefaults.Cache.Ehcache.timeToLiveSeconds;
			private long maxEntries = JHipsterDefaults.Cache.Ehcache.maxEntries;
		}

		@Getter
		@Setter
		public static class Infinispan {
			private String configFile = JHipsterDefaults.Cache.Infinispan.configFile;
			private boolean statsEnabled = JHipsterDefaults.Cache.Infinispan.statsEnabled;
			private final Local local = new Local();
			private final Distributed distributed = new Distributed();
			private final Replicated replicated = new Replicated();

			@Getter
			@Setter
			public static class Local {
				private long timeToLiveSeconds = JHipsterDefaults.Cache.Infinispan.Local.timeToLiveSeconds;
				private long maxEntries = JHipsterDefaults.Cache.Infinispan.Local.maxEntries;
			}

			@Getter
			@Setter
			public static class Distributed {
				private long timeToLiveSeconds = JHipsterDefaults.Cache.Infinispan.Distributed.timeToLiveSeconds;
				private long maxEntries = JHipsterDefaults.Cache.Infinispan.Distributed.maxEntries;
				private int instanceCount = JHipsterDefaults.Cache.Infinispan.Distributed.instanceCount;
			}

			@Getter
			@Setter
			public static class Replicated {
				private long timeToLiveSeconds = JHipsterDefaults.Cache.Infinispan.Replicated.timeToLiveSeconds;
				private long maxEntries = JHipsterDefaults.Cache.Infinispan.Replicated.maxEntries;
			}
		}

		@Getter
		@Setter
		public static class Memcached {
			private boolean enabled = JHipsterDefaults.Cache.Memcached.enabled;
			/**
			 * Comma or whitespace separated list of servers' addresses.
			 */
			private String servers = JHipsterDefaults.Cache.Memcached.servers;
			private int expiration = JHipsterDefaults.Cache.Memcached.expiration;
			private boolean useBinaryProtocol = JHipsterDefaults.Cache.Memcached.useBinaryProtocol;
			private Authentication authentication = new Authentication();

			@Getter
			@Setter
			public static class Authentication {
				private boolean enabled = JHipsterDefaults.Cache.Memcached.Authentication.enabled;
				private String username;
				private String password;
			}
		}

		@Getter
		@Setter
		public static class Redis {
			private String[] server = JHipsterDefaults.Cache.Redis.server;
			private int expiration = JHipsterDefaults.Cache.Redis.expiration;
			private boolean cluster = JHipsterDefaults.Cache.Redis.cluster;
			private int connectionPoolSize = JHipsterDefaults.Cache.Redis.connectionPoolSize;
			private int connectionMinimumIdleSize = JHipsterDefaults.Cache.Redis.connectionMinimumIdleSize;
			private int subscriptionConnectionPoolSize = JHipsterDefaults.Cache.Redis.subscriptionConnectionPoolSize;
			private int subscriptionConnectionMinimumIdleSize = JHipsterDefaults.Cache.Redis.subscriptionConnectionMinimumIdleSize;
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
		private final Map<String, Client> clients = new HashMap<>();
		private final RememberMe rememberMe = new RememberMe();
		private final OAuth2 oauth2 = new OAuth2();

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
			if (origin.equals("") && clients.containsKey("default")) return clients.get("default");
			return clients.get(origin.substring(1));
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
			private String defaultUser = "+user";
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
				private String accessTokenUri = JHipsterDefaults.Security.ClientAuthorization.accessTokenUri;
				private String tokenServiceId = JHipsterDefaults.Security.ClientAuthorization.tokenServiceId;
				private String clientId = JHipsterDefaults.Security.ClientAuthorization.clientId;
				private String clientSecret = JHipsterDefaults.Security.ClientAuthorization.clientSecret;
			}

			@Getter
			@Setter
			public static class Authentication {
				private final Jwt jwt = new Jwt();

				@Getter
				@Setter
				public static class Jwt {
					private String clientId = null;
					private String base64Secret = JHipsterDefaults.Security.Authentication.Jwt.base64Secret;
					private String secret = null;
					private String jwksUri = null;
					private String tokenEndpoint = null;
					private long tokenValidityInSeconds = JHipsterDefaults.Security.Authentication.Jwt.tokenValidityInSeconds;
					private long tokenValidityInSecondsForRememberMe = JHipsterDefaults.Security.Authentication.Jwt.tokenValidityInSecondsForRememberMe;

					public String getSecret() {
						if (secret == null) {
							secret = new String(Base64.getDecoder().decode(base64Secret));
						}
						return secret;
					}
				}
			}
		}

		@Getter
		@Setter
		public static class RememberMe {
			@NotNull
			private String key = JHipsterDefaults.Security.RememberMe.key;
		}

		@Getter
		@Setter
		public static class OAuth2 {
			private List<String> audience = new ArrayList<>();
		}
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
	public static class Logging {
		private boolean useJsonFormat = JHipsterDefaults.Logging.useJsonFormat;
		private final Logstash logstash = new Logstash();

		@Getter
		@Setter
		public static class Logstash {
			private boolean enabled = JHipsterDefaults.Logging.Logstash.enabled;
			private String host = JHipsterDefaults.Logging.Logstash.host;
			private int port = JHipsterDefaults.Logging.Logstash.port;
			private int queueSize = JHipsterDefaults.Logging.Logstash.queueSize;
		}
	}

	@Getter
	@Setter
	public static class Social {
		private String redirectAfterSignIn = JHipsterDefaults.Social.redirectAfterSignIn;
	}

	@Getter
	@Setter
	public static class Gateway {
		private final RateLimiting rateLimiting = new RateLimiting();
		private Map<String, List<String>> authorizedMicroservicesEndpoints = JHipsterDefaults.Gateway.authorizedMicroservicesEndpoints;

		@Getter
		@Setter
		public static class RateLimiting {
			private boolean enabled = JHipsterDefaults.Gateway.RateLimiting.enabled;
			private long limit = JHipsterDefaults.Gateway.RateLimiting.limit;
			private int durationInSeconds = JHipsterDefaults.Gateway.RateLimiting.durationInSeconds;
		}
	}

	@Getter
	@Setter
	public static class Registry {
		private String password = JHipsterDefaults.Registry.password;
	}

	@Getter
	@Setter
	public static class ClientApp {
		private String name = JHipsterDefaults.ClientApp.name;
	}

	@Getter
	@Setter
	public static class AuditEvents {
		private int retentionPeriod = JHipsterDefaults.AuditEvents.retentionPeriod;
	}
}
