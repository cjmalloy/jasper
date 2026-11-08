package jasper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.config.Config.SecurityConfig;
import jasper.config.Config.ServerConfig;
import jasper.domain.Template;
import jasper.repository.TemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins Jackson defaults of {@link ServerConfig} and {@link SecurityConfig}
 * as read from the _config/server and _config/security templates.
 */
@IntegrationTest
public class ConfigDefaultsIT {
	static final String SECURITY_ORIGIN = "@cfgdefaults";
	static final String TEST_BASE64_SECRET = "MjY0ZWY2ZTZhYmJhMTkyMmE5MTAxMTg3Zjc2ZDlmZWUwYjk0MDgzODA0MDJiOTgyNTk4MmNjYmQ4Yjg3MmVhYjk0MmE0OGFmNzE2YTQ5ZjliMTEyN2NlMWQ4MjA5OTczYjU2NzAxYTc4YThkMzYxNzdmOTk5MTIxODZhMTkwMDM=";

	static final String SERVER_DEFAULT_JSON = "{\"emailHost\":\"jasper.local\",\"maxSources\":1000,\"modSeals\":[\"seal\",\"+seal\",\"_seal\",\"_moderated\"],\"editorSeals\":[\"plugin/qc\"],\"webOrigins\":[\"\"],\"maxReplEntityBatch\":500,\"sshOrigins\":[\"\"],\"maxPushEntityBatch\":5000,\"maxPullEntityBatch\":5000,\"scriptSelectors\":[\"\"],\"hostBlacklist\":[\"*.local\"],\"maxConcurrentScripts\":100000,\"maxConcurrentReplication\":3,\"maxRequests\":500,\"maxRequestsPeriod\":\"PT0.0000005S\",\"maxConcurrentRequests\":500,\"maxConcurrentFetch\":10,\"storage\":\"local\",\"storageBucket\":\"\",\"storageRoutes\":[]}";
	static final String SECURITY_DEFAULT_JSON = "{\"mode\":\"\",\"clientId\":\"\",\"base64Secret\":\"\",\"secret\":\"\",\"jwksUri\":\"\",\"tokenEndpoint\":\"\",\"scimEndpoint\":\"\",\"usernameClaim\":\"sub\",\"externalId\":false,\"emailDomainInUsername\":false,\"rootEmailDomain\":\"\",\"verifiedEmailClaim\":\"verified_email\",\"authoritiesClaim\":\"auth\",\"readAccessClaim\":\"readAccess\",\"writeAccessClaim\":\"writeAccess\",\"tagReadAccessClaim\":\"tagReadAccess\",\"tagWriteAccessClaim\":\"tagWriteAccess\",\"minRole\":\"ROLE_ANONYMOUS\",\"minWriteRole\":\"ROLE_VIEWER\",\"minFetchRole\":\"ROLE_USER\",\"minConfigRole\":\"ROLE_ADMIN\",\"minReadBackupsRole\":\"ROLE_ADMIN\",\"defaultRole\":\"ROLE_ANONYMOUS\",\"defaultUser\":\"\",\"maxRequests\":50,\"maxRequestsPeriod\":\"PT0.0000005S\",\"maxConcurrentRequests\":0,\"maxConcurrentScripts\":5,\"scriptLimits\":{}}";

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ConfigCache configCache;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	Props props;

	ObjectNode originalServerConfig;

	@BeforeEach
	void init() {
		// Other tests delete all templates, recreate _config/server as on startup
		configCache.init();
		originalServerConfig = templateRepository.findByTemplateAndOrigin("_config/server", "")
			.map(Template::getConfig)
			.orElse(null);
		templateRepository.findByTemplateAndOrigin("_config/security", SECURITY_ORIGIN)
			.ifPresent(templateRepository::delete);
	}

	@AfterEach
	void cleanup() {
		templateRepository.findByTemplateAndOrigin("_config/server", "").ifPresent(t -> {
			t.setConfig(originalServerConfig);
			templateRepository.save(t);
		});
		templateRepository.findByTemplateAndOrigin("_config/security", SECURITY_ORIGIN)
			.ifPresent(templateRepository::delete);
	}

	static void assertServerDefaults(ServerConfig c, List<String> webOrigins) {
		assertThat(c.getEmailHost()).isEqualTo("jasper.local");
		assertThat(c.getMaxSources()).isEqualTo(1000);
		assertThat(c.getModSeals()).containsExactly("seal", "+seal", "_seal", "_moderated");
		assertThat(c.getEditorSeals()).containsExactly("plugin/qc");
		assertThat(c.getWebOrigins()).isEqualTo(webOrigins);
		assertThat(c.getMaxReplEntityBatch()).isEqualTo(500);
		assertThat(c.getSshOrigins()).containsExactly("");
		assertThat(c.getMaxPushEntityBatch()).isEqualTo(5000);
		assertThat(c.getMaxPullEntityBatch()).isEqualTo(5000);
		assertThat(c.getScriptSelectors()).containsExactly("");
		assertThat(c.getScriptWhitelist()).isNull();
		assertThat(c.getHostWhitelist()).isNull();
		assertThat(c.getHostBlacklist()).containsExactly("*.local");
		assertThat(c.getMaxConcurrentScripts()).isEqualTo(100_000);
		assertThat(c.getMaxConcurrentReplication()).isEqualTo(3);
		assertThat(c.getMaxRequests()).isEqualTo(500);
		assertThat(c.getMaxRequestsPeriod()).isEqualTo(Duration.ofNanos(500));
		assertThat(c.getMaxConcurrentRequests()).isEqualTo(500);
		assertThat(c.getMaxConcurrentFetch()).isEqualTo(10);
		assertThat(c.getStorage()).isEqualTo("local");
		assertThat(c.getStorageBucket()).isEqualTo("");
		assertThat(c.getStorageRoutes()).isNotNull().isEmpty();
		assertThat(c.get_webOriginsParsed()).isNull();
		assertThat(c.get_sshOriginsParsed()).isNull();
		assertThat(c.get_scriptSelectorsParsed()).isNull();
	}

	static void assertSecurityDefaults(SecurityConfig c, String mode, String base64Secret) {
		assertThat(c.getMode()).isEqualTo(mode);
		assertThat(c.getClientId()).isEqualTo("");
		assertThat(c.getBase64Secret()).isEqualTo(base64Secret);
		assertThat(c.getSecret()).isEqualTo("");
		assertThat(c.getJwksUri()).isEqualTo("");
		assertThat(c.getTokenEndpoint()).isEqualTo("");
		assertThat(c.getScimEndpoint()).isEqualTo("");
		assertThat(c.getUsernameClaim()).isEqualTo("sub");
		assertThat(c.isExternalId()).isFalse();
		assertThat(c.isEmailDomainInUsername()).isFalse();
		assertThat(c.getRootEmailDomain()).isEqualTo("");
		assertThat(c.getVerifiedEmailClaim()).isEqualTo("verified_email");
		assertThat(c.getAuthoritiesClaim()).isEqualTo("auth");
		assertThat(c.getReadAccessClaim()).isEqualTo("readAccess");
		assertThat(c.getWriteAccessClaim()).isEqualTo("writeAccess");
		assertThat(c.getTagReadAccessClaim()).isEqualTo("tagReadAccess");
		assertThat(c.getTagWriteAccessClaim()).isEqualTo("tagWriteAccess");
		assertThat(c.getMinRole()).isEqualTo("ROLE_ANONYMOUS");
		assertThat(c.getMinWriteRole()).isEqualTo("ROLE_VIEWER");
		assertThat(c.getMinFetchRole()).isEqualTo("ROLE_USER");
		assertThat(c.getMinConfigRole()).isEqualTo("ROLE_ADMIN");
		assertThat(c.getMinReadBackupsRole()).isEqualTo("ROLE_ADMIN");
		assertThat(c.getDefaultRole()).isEqualTo("ROLE_ANONYMOUS");
		assertThat(c.getDefaultUser()).isEqualTo("");
		assertThat(c.getDefaultReadAccess()).isNull();
		assertThat(c.getDefaultWriteAccess()).isNull();
		assertThat(c.getDefaultTagReadAccess()).isNull();
		assertThat(c.getDefaultTagWriteAccess()).isNull();
		assertThat(c.getMaxRequests()).isEqualTo(50);
		assertThat(c.getMaxRequestsPeriod()).isEqualTo(Duration.ofNanos(500));
		assertThat(c.getMaxConcurrentRequests()).isEqualTo(0);
		assertThat(c.getMaxConcurrentScripts()).isEqualTo(5);
		assertThat(c.getScriptLimits()).isNotNull().isEmpty();
		assertThat(c.get_scriptLimitsParsed()).isNull();
	}

	void saveServerConfig(String json) throws Exception {
		var template = templateRepository.findByTemplateAndOrigin("_config/server", "").orElseThrow();
		template.setConfig((ObjectNode) objectMapper.readTree(json));
		templateRepository.save(template);
	}

	void saveSecurityConfig(String json) throws Exception {
		var template = new Template();
		template.setTag("_config/security");
		template.setOrigin(SECURITY_ORIGIN);
		template.setConfig((ObjectNode) objectMapper.readTree(json));
		templateRepository.save(template);
	}

	// ---------- ServerConfig ----------

	@Test
	void serverEmptyObjectGivesAllDefaults() throws Exception {
		assertServerDefaults(objectMapper.convertValue(objectMapper.createObjectNode(), ServerConfig.class), List.of(""));
		assertServerDefaults(objectMapper.readValue("{}", ServerConfig.class), List.of(""));
	}

	@Test
	void serverDefaultSerialization() throws Exception {
		// Exact string pins property order, NON_NULL (null whitelists absent) and the Duration format
		assertThat(objectMapper.writeValueAsString(ServerConfig.builder().build())).isEqualTo(SERVER_DEFAULT_JSON);
		assertThat(objectMapper.writeValueAsString(new ServerConfig())).isEqualTo(SERVER_DEFAULT_JSON);
		assertThat(objectMapper.writeValueAsString(ServerConfig.builderFor("").build())).isEqualTo(SERVER_DEFAULT_JSON);
	}

	@Test
	void serverPartialObjectOnlyChangesGivenFields() throws Exception {
		var c = objectMapper.readValue("""
			{"emailHost": "example.com", "maxSources": 5, "modSeals": ["x"], "hostWhitelist": ["*.example.com"],
			 "maxRequestsPeriod": "PT1S", "storageRoutes": [{"bucket": "b"}]}
			""", ServerConfig.class);
		assertThat(c.getEmailHost()).isEqualTo("example.com");
		assertThat(c.getMaxSources()).isEqualTo(5);
		assertThat(c.getModSeals()).containsExactly("x");
		assertThat(c.getHostWhitelist()).containsExactly("*.example.com");
		assertThat(c.getMaxRequestsPeriod()).isEqualTo(Duration.ofSeconds(1));
		assertThat(c.getStorageRoutes()).hasSize(1);
		var route = c.getStorageRoutes().get(0);
		assertThat(route.getBucket()).isEqualTo("b");
		assertThat(route.getStorage()).isEqualTo("");
		assertThat(route.getNamespaces()).isNotNull().isEmpty();
		assertThat(route.getTenants()).isNotNull().isEmpty();
		assertThat(route.getCdnBaseUrl()).isEqualTo("");
		// Untouched fields
		assertThat(c.getEditorSeals()).containsExactly("plugin/qc");
		assertThat(c.getWebOrigins()).containsExactly("");
		assertThat(c.getMaxReplEntityBatch()).isEqualTo(500);
		assertThat(c.getSshOrigins()).containsExactly("");
		assertThat(c.getMaxPushEntityBatch()).isEqualTo(5000);
		assertThat(c.getMaxPullEntityBatch()).isEqualTo(5000);
		assertThat(c.getScriptSelectors()).containsExactly("");
		assertThat(c.getScriptWhitelist()).isNull();
		assertThat(c.getHostBlacklist()).containsExactly("*.local");
		assertThat(c.getMaxConcurrentScripts()).isEqualTo(100_000);
		assertThat(c.getMaxConcurrentReplication()).isEqualTo(3);
		assertThat(c.getMaxRequests()).isEqualTo(500);
		assertThat(c.getMaxConcurrentRequests()).isEqualTo(500);
		assertThat(c.getMaxConcurrentFetch()).isEqualTo(10);
		assertThat(c.getStorage()).isEqualTo("local");
		assertThat(c.getStorageBucket()).isEqualTo("");
	}

	@Test
	void serverExplicitNulls() throws Exception {
		var c = objectMapper.convertValue(objectMapper.readTree("""
			{"maxSources": null, "modSeals": null, "maxRequestsPeriod": null, "emailHost": null}
			"""), ServerConfig.class);
		// null on a scalar (int, String, Duration) keeps the default
		assertThat(c.getMaxSources()).isEqualTo(1000);
		assertThat(c.getMaxRequestsPeriod()).isEqualTo(Duration.ofNanos(500));
		assertThat(c.getEmailHost()).isEqualTo("jasper.local");
		// null on a List replaces the default with null (e.g. webOrigins: null disables web access)
		assertThat(c.getModSeals()).isNull();
		assertThat(c.getEditorSeals()).containsExactly("plugin/qc");
		assertThat(c.getMaxReplEntityBatch()).isEqualTo(500);
	}

	@Test
	void serverDurationFormats() throws Exception {
		assertThat(objectMapper.readValue("{\"maxRequestsPeriod\": \"PT0.5S\"}", ServerConfig.class).getMaxRequestsPeriod())
			.isEqualTo(Duration.ofMillis(500));
		// Numbers are read as (fractional) seconds
		assertThat(objectMapper.readValue("{\"maxRequestsPeriod\": 1.5}", ServerConfig.class).getMaxRequestsPeriod())
			.isEqualTo(Duration.ofMillis(1500));
		assertThat(objectMapper.readValue("{\"maxRequestsPeriod\": 2}", ServerConfig.class).getMaxRequestsPeriod())
			.isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void serverUnknownPropertyIsIgnored() throws Exception {
		assertServerDefaults(objectMapper.readValue("{\"unknown\": 1, \"_webOriginsParsed\": [\"x\"]}", ServerConfig.class), List.of(""));
	}

	@Test
	void serverInitTemplateStoresDefaults() {
		// ConfigCache.init() writes ServerConfig.builderFor(localOrigin) via convertValue(…, ObjectNode.class)
		assertThat((Object) originalServerConfig).isEqualTo(objectMapper.valueToTree(ServerConfig.builderFor("").build()));
	}

	@Test
	void serverConfigCacheEmptyTemplate() throws Exception {
		saveServerConfig("{}");

		assertServerDefaults(configCache.getTemplateConfig("_config/server", "", ServerConfig.class).orElseThrow(), List.of(""));
		// root() applies the test profile override (jasper.override.server.web-origins)
		assertServerDefaults(configCache.root(), props.getOverride().getServer().getWebOrigins());
		assertThat(configCache.root().getWebOrigins()).containsExactly("", "@a", "@a.b", "@b");
	}

	@Test
	void serverConfigCachePartialTemplate() throws Exception {
		saveServerConfig("{\"maxSources\": 7, \"scriptWhitelist\": [\"abc\"], \"maxConcurrentFetch\": null, \"hostBlacklist\": null}");

		var c = configCache.root();
		assertThat(c.getMaxSources()).isEqualTo(7);
		assertThat(c.getScriptWhitelist()).containsExactly("abc");
		assertThat(c.getMaxConcurrentFetch()).isEqualTo(10);
		assertThat(c.getHostBlacklist()).isNull();
		assertThat(c.getEmailHost()).isEqualTo("jasper.local");
		assertThat(c.getModSeals()).containsExactly("seal", "+seal", "_seal", "_moderated");
		assertThat(c.getMaxRequestsPeriod()).isEqualTo(Duration.ofNanos(500));
		assertThat(c.getStorageRoutes()).isNotNull().isEmpty();
	}

	// ---------- SecurityConfig ----------

	@Test
	void securityEmptyObjectGivesAllDefaults() throws Exception {
		assertSecurityDefaults(objectMapper.convertValue(objectMapper.createObjectNode(), SecurityConfig.class), "", "");
		assertSecurityDefaults(objectMapper.readValue("{}", SecurityConfig.class), "", "");
	}

	@Test
	void securityDefaultSerialization() throws Exception {
		assertThat(objectMapper.writeValueAsString(new SecurityConfig())).isEqualTo(SECURITY_DEFAULT_JSON);
		// getSecretBytes() is @JsonIgnore, the decoded secret is not serialized
		var withSecret = new SecurityConfig().withSecret("abc");
		assertThat(objectMapper.valueToTree(withSecret).has("secretBytes")).isFalse();
	}

	@Test
	void securityPartialObjectOnlyChangesGivenFields() throws Exception {
		var c = objectMapper.readValue("""
			{"minRole": "ROLE_USER", "maxConcurrentScripts": 2, "defaultReadAccess": ["public"],
			 "scriptLimits": {"@a": 3}, "externalId": true}
			""", SecurityConfig.class);
		assertThat(c.getMinRole()).isEqualTo("ROLE_USER");
		assertThat(c.getMaxConcurrentScripts()).isEqualTo(2);
		assertThat(c.getDefaultReadAccess()).containsExactly("public");
		assertThat(c.getScriptLimits()).isEqualTo(Map.of("@a", 3));
		assertThat(c.isExternalId()).isTrue();
		assertThat(c.getUsernameClaim()).isEqualTo("sub");
		assertThat(c.getMinWriteRole()).isEqualTo("ROLE_VIEWER");
		assertThat(c.getMaxRequests()).isEqualTo(50);
		assertThat(c.getMaxRequestsPeriod()).isEqualTo(Duration.ofNanos(500));
		assertThat(c.getDefaultWriteAccess()).isNull();
		assertThat(c.isEmailDomainInUsername()).isFalse();
	}

	@Test
	void securityExplicitNulls() throws Exception {
		var c = objectMapper.convertValue(objectMapper.readTree("""
			{"maxRequests": null, "scriptLimits": null, "defaultReadAccess": null, "usernameClaim": null, "externalId": null}
			"""), SecurityConfig.class);
		// null on a scalar keeps the default
		assertThat(c.getMaxRequests()).isEqualTo(50);
		assertThat(c.getUsernameClaim()).isEqualTo("sub");
		assertThat(c.isExternalId()).isFalse();
		// null on a Map / List replaces the default with null
		assertThat(c.getScriptLimits()).isNull();
		assertThat(c.getDefaultReadAccess()).isNull();
		assertThat(c.getMaxConcurrentScripts()).isEqualTo(5);
		// null scriptLimits falls back to maxConcurrentScripts
		assertThat(c.scriptLimit("plugin/script")).isEqualTo(5);
	}

	@Test
	void securityConfigCacheEmptyTemplate() throws Exception {
		saveSecurityConfig("{}");

		assertSecurityDefaults(configCache.getTemplateConfig("_config/security", SECURITY_ORIGIN, SecurityConfig.class).orElseThrow(), "", "");
		// security() applies the test profile overrides (mode and base64-secret)
		assertSecurityDefaults(configCache.security(SECURITY_ORIGIN), "jwt", TEST_BASE64_SECRET);
	}

	@Test
	void securityConfigCachePartialTemplate() throws Exception {
		saveSecurityConfig("{\"defaultRole\": \"ROLE_VIEWER\", \"maxConcurrentRequests\": 4, \"defaultTagReadAccess\": [], \"maxRequests\": null}");

		var c = configCache.security(SECURITY_ORIGIN);
		assertThat(c.getDefaultRole()).isEqualTo("ROLE_VIEWER");
		assertThat(c.getMaxConcurrentRequests()).isEqualTo(4);
		// Empty list stays empty, absent list stays null
		assertThat(c.getDefaultTagReadAccess()).isNotNull().isEmpty();
		assertThat(c.getDefaultTagWriteAccess()).isNull();
		assertThat(c.getMaxRequests()).isEqualTo(50);
		assertThat(c.getMode()).isEqualTo("jwt");
		assertThat(c.getMinRole()).isEqualTo("ROLE_ANONYMOUS");
		assertThat(c.getMaxConcurrentScripts()).isEqualTo(5);
		assertThat(c.getScriptLimits()).isNotNull().isEmpty();
	}
}
