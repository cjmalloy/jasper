package jasper.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jasper.repository.spec.QualifiedTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

import java.io.Serializable;
import java.util.Base64;
import java.util.List;

import static jasper.repository.spec.QualifiedTag.tagOriginList;
import static jasper.repository.spec.QualifiedTag.tagOriginSelector;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
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
		@Builder.Default
		private int maxReplEntityBatch = 500;
		/**
		 * Whitelist origins to be allowed to open SSH tunnels.
		 */
		private List<String> sshOrigins = List.of("");
		@Builder.Default
		private int maxPushEntityBatch = 5000;
		@Builder.Default
		private int maxPullEntityBatch = 5000;
		/**
		 * Whitelist selectors to run scripts on. No origin wildcards.
		 */
		private List<String> scriptSelectors = List.of("");
		@JsonIgnore
		private List<QualifiedTag> _scriptSelectors = null;
		@JsonIgnore
		public boolean script(String plugin) {
			if (scriptSelectors == null) return false;
			if (_scriptSelectors == null) _scriptSelectors = tagOriginList(scriptSelectors);
			return _scriptSelectors.stream().anyMatch(s -> s.captures(tagOriginSelector(plugin + s.origin)));
		}
		@JsonIgnore
		public boolean script(String plugin, String origin) {
			if (scriptSelectors == null) return false;
			if (_scriptSelectors == null) _scriptSelectors = tagOriginList(scriptSelectors);
			return _scriptSelectors.stream().anyMatch(s -> s.captures(tagOriginSelector(plugin + origin)));
		}
		@JsonIgnore
		public List<String> scriptOrigins(String plugin) {
			if (scriptSelectors == null) return List.of();
			if (_scriptSelectors == null) _scriptSelectors = tagOriginList(scriptSelectors);
			return _scriptSelectors.stream().filter(s -> s.captures(tagOriginSelector(plugin + s.origin))).map(s -> s.origin).toList();
		}
		/**
		 * Whitelist script SHA-256 hashes allowed to run. Allows any scripts if empty.
		 */
		private List<String> scriptWhitelist = null;
		/**
		 * Whitelist domains to be allowed to fetch from.
		 */
		private List<String> hostWhitelist = null;
		/**
		 * Blacklist domains to be allowed to fetch from. Takes precedence over domain whitelist.
		 */
		private List<String> hostBlacklist = List.of("*.local");

		public ServerConfig wrap(Props props) {
			var wrapped = this;
			var server = props.getOverride().getServer();
			if (isNotBlank(server.getEmailHost())) wrapped = wrapped.withEmailHost(server.getEmailHost());
			if (server.getMaxSources() != null) wrapped = wrapped.withMaxSources(server.getMaxSources());
			if (isNotEmpty(server.getModSeals())) wrapped = wrapped.withModSeals(server.getModSeals());
			if (isNotEmpty(server.getEditorSeals())) wrapped = wrapped.withEditorSeals(server.getEditorSeals());
			if (isNotEmpty(server.getWebOrigins())) wrapped = wrapped.withWebOrigins(server.getWebOrigins());
			if (server.getMaxReplEntityBatch() != null) wrapped = wrapped.withMaxReplEntityBatch(server.getMaxReplEntityBatch());
			if (isNotEmpty(server.getSshOrigins())) wrapped = wrapped.withSshOrigins(server.getSshOrigins());
			if (server.getMaxPushEntityBatch() != null) wrapped = wrapped.withMaxPushEntityBatch(server.getMaxPushEntityBatch());
			if (server.getMaxPullEntityBatch() != null) wrapped = wrapped.withMaxPullEntityBatch(server.getMaxPullEntityBatch());
			if (isNotEmpty(server.getScriptSelectors())) wrapped = wrapped.withScriptSelectors(server.getScriptSelectors());
			if (isNotEmpty(server.getScriptWhitelist())) wrapped = wrapped.withScriptWhitelist(server.getScriptWhitelist());
			if (isNotEmpty(server.getHostWhitelist())) wrapped = wrapped.withHostWhitelist(server.getHostWhitelist());
			if (isNotEmpty(server.getHostBlacklist())) wrapped = wrapped.withHostBlacklist(server.getHostBlacklist());
			return wrapped;
		}

		public static ServerConfigBuilder builderFor(String origin) {
			return ServerConfig.builder()
				.webOrigins(List.of(origin))
				.sshOrigins(List.of(origin))
				.scriptSelectors(List.of(isBlank(origin) ? "" : origin));
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
