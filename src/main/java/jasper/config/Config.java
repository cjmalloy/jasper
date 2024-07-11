package jasper.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

import java.io.Serializable;
import java.util.Base64;
import java.util.List;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public interface Config {
	/**
	 * Root Config installed to _config/server template.
	 * Template will be created with these default values if it does not exist.
	 */
	@Getter
	@Setter
	@Builder
	@With
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonInclude(JsonInclude.Include.NON_NULL)
	class ServerConfig implements Serializable {
		@Builder.Default
		private String emailHost = "jasper.local";
		@Builder.Default
		private int maxSources = 1000;
		@Builder.Default
		private List<String> modSeals = List.of("seal", "+seal", "_seal", "_moderated");
		@Builder.Default
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
		@Builder.Default
		private int maxPushEntityBatch = 5000;
		/**
		 * Whitelist origins to be allowed to pull using +plugin/origin/pull.
		 */
		private List<String> pullOrigins = List.of("");
		@Builder.Default
		private int maxPullEntityBatch = 5000;
		/**
		 * Whitelist origins to run scripts on.
		 */
		private List<String> scriptOrigins = List.of("");
		/**
		 * Whitelist domains to be allowed to scrape.
		 */
		private List<String> scrapeHostWhitelist = null;
		/**
		 * Blacklist domains to be allowed to scrape. Takes precedence over domain whitelist.
		 */
		private List<String> scrapeHostBlacklist = List.of("*.local");

		public ServerConfig wrap(Props props) {
			var wrapped = this;
			var server = props.getOverride().getServer();
			if (isNotBlank(server.getEmailHost())) wrapped = wrapped.withEmailHost(server.getEmailHost());
			if (server.getMaxSources() != null) wrapped = wrapped.withMaxSources(server.getMaxSources());
			if (isNotEmpty(server.getModSeals())) wrapped = wrapped.withModSeals(server.getModSeals());
			if (isNotEmpty(server.getEditorSeals())) wrapped = wrapped.withEditorSeals(server.getEditorSeals());
			if (isNotEmpty(server.getWebOrigins())) wrapped = wrapped.withWebOrigins(server.getWebOrigins());
			if (isNotEmpty(server.getSshOrigins())) wrapped = wrapped.withSshOrigins(server.getSshOrigins());
			if (isNotEmpty(server.getPushOrigins())) wrapped = wrapped.withPushOrigins(server.getPushOrigins());
			if (server.getMaxPushEntityBatch() != null) wrapped = wrapped.withMaxPushEntityBatch(server.getMaxPushEntityBatch());
			if (isNotEmpty(server.getPullOrigins())) wrapped = wrapped.withPullOrigins(server.getPullOrigins());
			if (server.getMaxPullEntityBatch() != null) wrapped = wrapped.withMaxPullEntityBatch(server.getMaxPullEntityBatch());
			if (isNotEmpty(server.getScriptOrigins())) wrapped = wrapped.withScriptOrigins(server.getScriptOrigins());
			if (isNotEmpty(server.getScrapeHostWhitelist())) wrapped = wrapped.withScrapeHostWhitelist(server.getScrapeHostWhitelist());
			if (isNotEmpty(server.getScrapeHostBlacklist())) wrapped = wrapped.withScrapeHostBlacklist(server.getScrapeHostBlacklist());
			return wrapped;
		}

		public static ServerConfigBuilder builderFor(String origin) {
			return ServerConfig.builder()
				.webOrigins(List.of(origin))
				.sshOrigins(List.of(origin))
				.pushOrigins(List.of(origin))
				.pullOrigins(List.of(origin))
				.scriptOrigins(List.of(origin));
		}
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
		private boolean emailDomainInUsername = false;
		private String rootEmailDomain = "";
		private String verifiedEmailClaim = "verified_email";
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
		/**
		 * Default user tag given to every logged out user.
		 */
		private String defaultUser = "";
		private List<String> defaultReadAccess;
		private List<String> defaultWriteAccess;
		private List<String> defaultTagReadAccess;
		private List<String> defaultTagWriteAccess;

		public byte[] getSecretBytes() {
			if (isNotBlank(secret)) return secret.getBytes();
			return Base64.getDecoder().decode(base64Secret);
		}

		public SecurityConfig wrap(Props props) {
			var wrapped = this;
			var security = props.getOverride().getSecurity();
			if (isNotBlank(security.getMode())) wrapped = wrapped.withMode(security.getMode());
			if (isNotBlank(security.getClientId())) wrapped = wrapped.withClientId(security.getClientId());
			if (isNotBlank(security.getSecret()) || isNotBlank(security.getBase64Secret())) {
				wrapped = wrapped.withBase64Secret(security.getBase64Secret());
				wrapped = wrapped.withSecret(security.getSecret());
			}
			if (isNotBlank(security.getJwksUri())) wrapped = wrapped.withJwksUri(security.getJwksUri());
			if (isNotBlank(security.getUsernameClaim())) wrapped = wrapped.withUsernameClaim(security.getUsernameClaim());
			if (!"unset".equals(security.getVerifiedEmailClaim())) wrapped = wrapped.withVerifiedEmailClaim(security.getVerifiedEmailClaim());
			if (isNotBlank(security.getDefaultUser())) wrapped = wrapped.withDefaultUser(security.getDefaultUser());
			if (isNotBlank(security.getTokenEndpoint())) wrapped = wrapped.withTokenEndpoint(security.getTokenEndpoint());
			if (isNotBlank(security.getScimEndpoint())) wrapped = wrapped.withScimEndpoint(security.getScimEndpoint());
			return wrapped;
		}
	}
}
