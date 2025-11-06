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
import java.util.Map;

import static jasper.repository.spec.QualifiedTag.tagOriginList;
import static jasper.repository.spec.QualifiedTag.tagOriginSelector;
import static java.lang.Math.min;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toMap;
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
		@Builder.Default
		private List<String> webOrigins = List.of("");
		@Builder.Default
		private int maxReplEntityBatch = 500;
		/**
		 * Whitelist origins to be allowed to open SSH tunnels.
		 */
		@Builder.Default
		private List<String> sshOrigins = List.of("");
		@Builder.Default
		private int maxPushEntityBatch = 5000;
		@Builder.Default
		private int maxPullEntityBatch = 5000;
		/**
		 * Whitelist selectors to run scripts on. No origin wildcards.
		 */
		@Builder.Default
		private List<String> scriptSelectors = List.of("");
		@JsonIgnore
		@Builder.Default
		private List<QualifiedTag> _scriptSelectorsParsed = null;
		@JsonIgnore
		public List<QualifiedTag> scriptSelectorsParsed() {
			if (scriptSelectors == null) return null;
			if (_scriptSelectorsParsed == null) _scriptSelectorsParsed = tagOriginList(scriptSelectors);
			return _scriptSelectorsParsed;
		}
		@JsonIgnore
		public boolean script(String plugin) {
			if (scriptSelectorsParsed() == null) return false;
			return scriptSelectorsParsed().stream().anyMatch(s -> s.captures(tagOriginSelector(plugin + s.origin)));
		}
		@JsonIgnore
		public boolean script(String plugin, String origin) {
			if (scriptSelectorsParsed() == null) return false;
			return scriptSelectorsParsed().stream().anyMatch(s -> s.captures(tagOriginSelector(plugin + origin)));
		}
		@JsonIgnore
		public List<String> scriptOrigins(String plugin) {
			if (scriptSelectorsParsed() == null) return List.of();
			return scriptSelectorsParsed().stream().filter(s -> s.captures(tagOriginSelector(plugin + s.origin))).map(s -> s.origin).toList();
		}
		/**
		 * Whitelist script SHA-256 hashes allowed to run. Allows any scripts if empty.
		 */
		@Builder.Default
		private List<String> scriptWhitelist = null;
		/**
		 * Whitelist domains to be allowed to fetch from.
		 */
		@Builder.Default
		private List<String> hostWhitelist = null;
		/**
		 * Blacklist domains to be allowed to fetch from. Takes precedence over domain whitelist.
		 */
		@Builder.Default
		private List<String> hostBlacklist = List.of("*.local");
		/**
		 * Maximum concurrent script executions. Default 5.
		 */
		@Builder.Default
		private int maxConcurrentScripts = 5;
		/**
		 * Maximum concurrent replication push/pull operations. Default 3.
		 */
		@Builder.Default
		private int maxConcurrentReplication = 3;
		/**
		 * Maximum HTTP requests per origin evert 500 nanoseconds. Default 50.
		 */
		@Builder.Default
		private int maxRequests = 50;
		/**
		 * Global maximum concurrent HTTP requests (across all origins). Default 500.
		 */
		@Builder.Default
		private int maxConcurrentRequests = 500;
		/**
		 * Maximum concurrent fetch operations (scraping). Default 10.
		 */
		@Builder.Default
		private int maxConcurrentFetch = 10;

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
			if (server.getMaxRequests() != null) wrapped = wrapped.withMaxRequests(server.getMaxRequests());
			if (server.getMaxConcurrentRequests() != null) wrapped = wrapped.withMaxConcurrentRequests(server.getMaxConcurrentRequests());
			if (server.getMaxConcurrentScripts() != null) wrapped = wrapped.withMaxConcurrentScripts(server.getMaxConcurrentScripts());
			if (server.getMaxConcurrentReplication() != null) wrapped = wrapped.withMaxConcurrentReplication(server.getMaxConcurrentReplication());
			if (server.getMaxConcurrentFetch() != null) wrapped = wrapped.withMaxConcurrentFetch(server.getMaxConcurrentFetch());
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
		private boolean externalId = false;
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
		 * Minimum role for fetching external resources.
		 */
		private String minFetchRole = "ROLE_USER";
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
		/**
		 * Maximum HTTP requests per origin evert 500 nanoseconds. Default 50.
		 */
		private int maxRequests = 50;
		/**
		 * Maximum concurrent script executions per origin. Default 100_000.
		 */
		private int maxConcurrentScripts = 100_000;
		/**
		 * Per-origin script execution limits. Map of origin selector patterns (origin, or tag+origin) to max concurrent value.
		 * No origin wildcards.
		 * Example: {"@myorg": 20, "+plugin/delta@myorg": 15, "_plugin/delta": 5}
		 * If more than one matches, the smallest limit is chosen.
		 */
		private Map<String, Integer> scriptLimits = Map.of();
		@JsonIgnore
		private Map<QualifiedTag, Integer> _scriptLimitsParsed = null;
		@JsonIgnore
		public Map<QualifiedTag, Integer> scriptLimitsParsed() {
			if (scriptLimits == null) return null;
			if (_scriptLimitsParsed == null) _scriptLimitsParsed = scriptLimits.entrySet().stream().collect(toMap(e -> tagOriginSelector(e.getKey()), Map.Entry::getValue));
			return _scriptLimitsParsed;
		}
		@JsonIgnore
		public Integer scriptLimit(String plugin) {
			if (scriptLimitsParsed() == null) return maxConcurrentScripts;
			return min(maxConcurrentScripts, scriptLimitsParsed().entrySet().stream()
				.filter(e -> e.getKey().captures(tagOriginSelector(plugin + e.getKey().origin)))
				.min(comparingInt(Map.Entry::getValue))
				.map(Map.Entry::getValue)
				.orElse(maxConcurrentScripts));
		}
		@JsonIgnore
		public Integer scriptLimit(String plugin, String origin) {
			if (scriptLimitsParsed() == null) return maxConcurrentScripts;
			return min(maxConcurrentScripts, scriptLimitsParsed().entrySet().stream()
				.filter(e -> e.getKey().captures(tagOriginSelector(plugin + origin)))
				.min(comparingInt(Map.Entry::getValue))
				.map(Map.Entry::getValue)
				.orElse(maxConcurrentScripts));
		}

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
			if (security.getMaxRequests() != null) wrapped = wrapped.withMaxRequests(security.getMaxRequests());
			if (security.getMaxConcurrentScripts() != null) wrapped = wrapped.withMaxConcurrentScripts(security.getMaxConcurrentScripts());
			return wrapped;
		}
	}
}
