package jasper.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Base64;
import java.util.List;

public interface Config {
	/**
	 * Root Config installed to _config/server template.
	 * Template will be created with these default values if it does not exist.
	 */
	@Getter
	@Setter
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonInclude(JsonInclude.Include.NON_NULL)
	class ServerConfig implements Serializable {
		private String emailHost = "jasper.local";
		private int maxSources = 1000;
		private List<String> modSeals = List.of("seal", "+seal", "_seal", "_moderated");
		private List<String> editorSeals = List.of("plugin/qc");
		/**
		 * Tags for config Refs to be cached.
		 */
		private List<String> cacheTags = List.of("_config/server", "_config/security", "+plugin/scrape", "+plugin/oembed");
		/**
		 * Whitelist origins to be allowed web traffic.
		 */
		private List<String> webOrigins = List.of("");
		/**
		 * Whitelist origins to be allowed to open SSH tunnels.
		 */
		private List<String> sshOrigins = List.of("");
		/**
		 * Whitelist origins to be allowed to replicate using +plugin/origin.
		 */
		private List<String> replicateOrigins = List.of("");
		private int maxReplicateBatch = 5000;
		/**
		 * Whitelist origins to run async tasks on.
		 */
		private List<String> asyncOrigins = List.of("");
		/**
		 * Whitelist origins to be allowed to scrape using +plugin/feed.
		 */
		private List<String> scrapeOrigins = List.of("");
		/**
		 * Whitelist domains to be allowed to scrape.
		 */
		private List<String> scrapeHostWhitelist = null;
		/**
		 * Blacklist domains to be allowed to scrape. Takes precedence over domain whitelist.
		 */
		private List<String> scrapeHostBlacklist = List.of("*.local");
	}

	/**
	 * Tenant Config installed to _config/security template in each tenant.
	 */
	@Getter
	@Setter
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonInclude(JsonInclude.Include.NON_NULL)
	class SecurityConfig implements Serializable {
		private String mode = "jwt";
		private String clientId = null;
		private String base64Secret;
		private String secret = null;
		private String jwksUri = null;
		private String tokenEndpoint = null;
		private long tokenValidityInSeconds = 1800;
		private long tokenValidityInSecondsForRememberMe = 2592000;
		private String clientSecret;
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

		public String getSecret() {
			if (secret == null) {
				secret = new String(Base64.getDecoder().decode(base64Secret));
			}
			return secret;
		}
	}
}
