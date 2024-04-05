package jasper.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

import java.io.Serializable;
import java.util.Base64;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public interface Config {
	/**
	 * Root Config installed to _config/server template.
	 * Template will be created with these default values if it does not exist.
	 */
	@Getter
	@Setter
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonInclude(JsonInclude.Include.NON_NULL)
	class ServerConfig implements Serializable {
		private String emailHost = "jasper.local";
		private int maxSources = 1000;
		private List<String> modSeals = List.of("seal", "+seal", "_seal", "_moderated");
		private List<String> editorSeals = List.of("plugin/qc");
		/**
		 * Whitelist origins to be allowed web access.
		 */
		private List<String> webOrigins = List.of("");
		/**
		 * Whitelist origins to be allowed to open SSH tunnels.
		 */
		private List<String> sshOrigins = List.of("");
		/**
		 * Whitelist origins to be allowed to push using +plugin/origin/push.
		 */
		private List<String> pushOrigins = List.of("");
		private int pushBatchSize = 20;
		private int maxPushEntityBatch = 5000;
		/**
		 * Whitelist origins to be allowed to pull using +plugin/origin/pull.
		 */
		private List<String> pullOrigins = List.of("");
		private int pullBatchSize = 20;
		private int maxPullEntityBatch = 5000;
		/**
		 * Whitelist origins to run async tasks on.
		 */
		private List<String> asyncOrigins = List.of("");
		/**
		 * Whitelist origins to be allowed to scrape using +plugin/feed.
		 */
		private List<String> scrapeOrigins = List.of("");
		private int scrapeBatchSize = 100;
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
	@With
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonInclude(JsonInclude.Include.NON_NULL)
	class SecurityConfig implements Serializable {
		private String mode = "";
		private String clientId = "";
		private String base64Secret = "";
		private String secret = "";
		private String jwksUri = "";
		private String tokenEndpoint = "";
		private String scimEndpoint = "";
		private String usernameClaim = "sub";
		private String authoritiesClaim = "auth";
		private String readAccessClaim = "readAccess";
		private String writeAccessClaim = "writeAccess";
		private String tagReadAccessClaim = "tagReadAccess";
		private String tagWriteAccessClaim = "tagWriteAccess";
		/**
		 * Minimum role for basic access.
		 */
		private String minRole = "ROLE_ANONYMOUS";
		/**
		 * Minimum role for writing.
		 */
		private String minWriteRole = "ROLE_ANONYMOUS";
		/**
		 * Default role given to every user.
		 */
		private String defaultRole = "ROLE_ANONYMOUS";
		/**
		 * Default user tag given to every logged out user.
		 */
		private String defaultUser = "";
		private List<String> defaultReadAccess;
		private List<String> defaultWriteAccess;
		private List<String> defaultTagReadAccess;
		private List<String> defaultTagWriteAccess;

		public String getSecret() {
			if (isBlank(secret)) {
				secret = new String(Base64.getDecoder().decode(base64Secret));
			}
			return secret;
		}

		public SecurityConfig wrap(Props props) {
			var wrapped = this;
			var security = props.getOverride().getSecurity();
			if (isNotBlank(security.getMode())) wrapped = wrapped.withMode(security.getMode());
			if (isNotBlank(security.getClientId())) wrapped = wrapped.withClientId(security.getClientId());
			if (isNotBlank(security.getBase64Secret())) wrapped = wrapped.withBase64Secret(security.getBase64Secret());
			if (isNotBlank(security.getSecret())) wrapped = wrapped.withSecret(security.getSecret());
			if (isNotBlank(security.getJwksUri())) wrapped = wrapped.withJwksUri(security.getJwksUri());
			if (isNotBlank(security.getUsernameClaim())) wrapped = wrapped.withUsernameClaim(security.getUsernameClaim());
			if (isNotBlank(security.getDefaultUser())) wrapped = wrapped.withDefaultUser(security.getDefaultUser());
			if (isNotBlank(security.getTokenEndpoint())) wrapped = wrapped.withTokenEndpoint(security.getTokenEndpoint());
			if (isNotBlank(security.getScimEndpoint())) wrapped = wrapped.withScimEndpoint(security.getScimEndpoint());
			return wrapped;
		}
	}
}
