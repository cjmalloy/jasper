package jasper.plugin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.config.JacksonConfiguration;
import jasper.config.ObjectMapperConfig;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins Jackson behaviour of the @Jacksonized builders {@link Index} and {@link Script}.
 */
@IntegrationTest
public class IndexScriptJacksonIT {
	static final String SCRIPT_TAG = "plugin/script/jacksontest";
	static final String URL = "test:index-script-jackson";

	static final String INDEX_DEFAULT_JSON = "{\"tags\":true,\"sources\":true,\"alts\":true,\"responses\":true,\"internalResponses\":true,\"fulltext\":true,\"published\":true,\"modified\":true,\"created\":true,\"title\":true,\"origin\":true,\"newResponse\":true,\"newReaction\":true,\"hotTags\":[],\"hotPlugins\":[],\"hotResponses\":[],\"hotRemoteResponses\":[],\"ignored\":true,\"cascade\":true,\"regen\":true}";
	static final String SCRIPT_DEFAULT_JSON = "{\"timeoutMs\":30000,\"language\":\"javascript\",\"format\":\"json\"}";

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ObjectMapperConfig objectMapperConfig;

	@Autowired
	ConfigCache configCache;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	RefRepository refRepository;

	ObjectNode originalIndexConfig;

	@BeforeEach
	void init() {
		// Unit tests replace the static mapper, restore the production wiring
		ReflectionTestUtils.invokeMethod(objectMapperConfig, "initStatic");
		// Other tests delete all templates, recreate _config/index as on startup
		configCache.init();
		originalIndexConfig = templateRepository.findByTemplateAndOrigin("_config/index", "")
			.map(Template::getConfig)
			.orElse(null);
		pluginRepository.deleteAll();
		refRepository.deleteAll();
	}

	@AfterEach
	void cleanup() {
		templateRepository.findByTemplateAndOrigin("_config/index", "").ifPresent(t -> {
			t.setConfig(originalIndexConfig);
			templateRepository.save(t);
		});
		pluginRepository.deleteAll();
		refRepository.deleteAll();
	}

	static void assertIndexDefaults(Index index) {
		assertThat(index.isTags()).isTrue();
		assertThat(index.isSources()).isTrue();
		assertThat(index.isAlts()).isTrue();
		assertThat(index.isResponses()).isTrue();
		assertThat(index.isInternalResponses()).isTrue();
		assertThat(index.isFulltext()).isTrue();
		assertThat(index.isPublished()).isTrue();
		assertThat(index.isModified()).isTrue();
		assertThat(index.isCreated()).isTrue();
		assertThat(index.isTitle()).isTrue();
		assertThat(index.isOrigin()).isTrue();
		assertThat(index.isNewResponse()).isTrue();
		assertThat(index.isNewReaction()).isTrue();
		assertThat(index.getHotTags()).isNotNull().isEmpty();
		assertThat(index.getHotPlugins()).isNotNull().isEmpty();
		assertThat(index.getHotResponses()).isNotNull().isEmpty();
		assertThat(index.getHotRemoteResponses()).isNotNull().isEmpty();
		assertThat(index.isIgnored()).isTrue();
		assertThat(index.isCascade()).isTrue();
		assertThat(index.isRegen()).isTrue();
	}

	static void assertScriptDefaults(Script script) {
		assertThat(script.getTimeoutMs()).isEqualTo(30_000);
		assertThat(script.getLanguage()).isEqualTo("javascript");
		assertThat(script.getFormat()).isEqualTo("json");
		assertThat(script.getRequirements()).isNull();
		assertThat(script.getScript()).isNull();
	}

	@Test
	void staticMapperIsTheSpringMapper() {
		// Ref/Template/Plugin use JacksonConfiguration.om() in production
		assertThat(JacksonConfiguration.om()).isSameAs(objectMapper);
	}

	// ---------- Index ----------

	@Test
	void indexEmptyObjectGivesAllDefaults() throws Exception {
		assertIndexDefaults(objectMapper.readValue("{}", Index.class));
		assertIndexDefaults(objectMapper.convertValue(objectMapper.createObjectNode(), Index.class));
	}

	@Test
	void indexPartialObjectOnlyChangesGivenFields() throws Exception {
		var index = objectMapper.readValue("""
			{"fulltext": false, "regen": false, "hotTags": ["plugin/progress:num"]}
			""", Index.class);

		assertThat(index.isFulltext()).isFalse();
		assertThat(index.isRegen()).isFalse();
		assertThat(index.getHotTags()).containsExactly("plugin/progress:num");
		assertThat(index.isTags()).isTrue();
		assertThat(index.isSources()).isTrue();
		assertThat(index.isAlts()).isTrue();
		assertThat(index.isResponses()).isTrue();
		assertThat(index.isInternalResponses()).isTrue();
		assertThat(index.isPublished()).isTrue();
		assertThat(index.isModified()).isTrue();
		assertThat(index.isCreated()).isTrue();
		assertThat(index.isTitle()).isTrue();
		assertThat(index.isOrigin()).isTrue();
		assertThat(index.isNewResponse()).isTrue();
		assertThat(index.isNewReaction()).isTrue();
		assertThat(index.getHotPlugins()).isNotNull().isEmpty();
		assertThat(index.getHotResponses()).isNotNull().isEmpty();
		assertThat(index.getHotRemoteResponses()).isNotNull().isEmpty();
		assertThat(index.isIgnored()).isTrue();
		assertThat(index.isCascade()).isTrue();
	}

	@Test
	void indexExplicitNullBooleanKeepsDefault() throws Exception {
		// @JsonSetter(nulls = SKIP): an explicit null keeps the @Builder.Default (true)
		var index = objectMapper.readValue("{\"tags\": null}", Index.class);
		assertThat(index.isTags()).isTrue();
		assertThat(index.isSources()).isTrue();

		var converted = objectMapper.convertValue(objectMapper.readTree("{\"tags\": null}"), Index.class);
		assertThat(converted.isTags()).isTrue();
		assertThat(converted.isSources()).isTrue();
	}

	@Test
	void indexExplicitNullListIsNull() throws Exception {
		// Lists keep an explicit null (the index code treats null like empty)
		var index = objectMapper.readValue("{\"hotTags\": null}", Index.class);
		assertThat(index.getHotTags()).isNull();
		assertThat(index.getHotPlugins()).isNotNull().isEmpty();
		assertThat(objectMapper.writeValueAsString(index)).doesNotContain("hotTags");
	}

	@Test
	void indexStringBooleanIsCoerced() throws Exception {
		assertThat(objectMapper.readValue("{\"tags\": \"false\"}", Index.class).isTags()).isFalse();
		assertThat(objectMapper.readValue("{\"tags\": \"true\"}", Index.class).isTags()).isTrue();
	}

	@Test
	void indexUnknownPropertyIsIgnored() throws Exception {
		var index = objectMapper.readValue("{\"unknown\": 1, \"other\": {\"a\": [1]}}", Index.class);
		assertIndexDefaults(index);
	}

	@Test
	void indexDefaultSerialization() throws Exception {
		// Exact string pins property order (declaration order, not alphabetical)
		assertThat(objectMapper.writeValueAsString(Index.builder().build())).isEqualTo(INDEX_DEFAULT_JSON);
		assertThat((Object) objectMapper.valueToTree(Index.builder().build()))
			.isEqualTo(objectMapper.readTree(INDEX_DEFAULT_JSON));
	}

	@Test
	void indexRoundTrip() throws Exception {
		var index = Index.builder()
			.fulltext(false)
			.hotPlugins(new ArrayList<>(List.of("plugin/test->size:num")))
			.build();
		var json = objectMapper.writeValueAsString(index);
		assertThat(json).isEqualTo(INDEX_DEFAULT_JSON
			.replace("\"fulltext\":true", "\"fulltext\":false")
			.replace("\"hotPlugins\":[]", "\"hotPlugins\":[\"plugin/test->size:num\"]"));
		var read = objectMapper.readValue(json, Index.class);
		assertThat(read.isFulltext()).isFalse();
		assertThat(read.getHotPlugins()).containsExactly("plugin/test->size:num");
		assertThat(read.isTags()).isTrue();
	}

	@Test
	void indexTemplateGetConfig() throws Exception {
		var template = new Template();
		template.setTag("_config/index");
		template.setConfig((ObjectNode) objectMapper.readTree("{}"));
		assertIndexDefaults(template.getConfig(Index.class));

		template.setConfig((ObjectNode) objectMapper.readTree("{\"cascade\": false, \"hotResponses\": [\"plugin/comment\"], \"extra\": 1}"));
		var index = template.getConfig(Index.class);
		assertThat(index.isCascade()).isFalse();
		assertThat(index.getHotResponses()).containsExactly("plugin/comment");
		assertThat(index.isTags()).isTrue();
		assertThat(index.getHotTags()).isNotNull().isEmpty();

		template.setConfig(null);
		assertThat(template.getConfig(Index.class)).isNull();
	}

	@Test
	void indexInitTemplateStoresDefaults() {
		// ConfigCache.init() writes Index.builder().build() via convertValue(…, ObjectNode.class)
		assertThat((Object) originalIndexConfig).isEqualTo(objectMapper.valueToTree(Index.builder().build()));
	}

	@Test
	void indexConfigCacheEmptyTemplate() throws Exception {
		saveIndexConfig("{}");
		assertIndexDefaults(configCache.index());
	}

	@Test
	void indexConfigCachePartialTemplate() throws Exception {
		saveIndexConfig("{\"newReaction\": false, \"title\": null, \"hotRemoteResponses\": [\"plugin/comment\"]}");

		var index = configCache.index();
		assertThat(index.isNewReaction()).isFalse();
		// Explicit null on a boolean keeps the default
		assertThat(index.isTitle()).isTrue();
		assertThat(index.getHotRemoteResponses()).containsExactly("plugin/comment");
		assertThat(index.isTags()).isTrue();
		assertThat(index.isNewResponse()).isTrue();
		assertThat(index.getHotTags()).isNotNull().isEmpty();
	}

	@Test
	void indexConfigCacheNullConfig() {
		var template = templateRepository.findByTemplateAndOrigin("_config/index", "").orElseThrow();
		template.setConfig(null);
		templateRepository.save(template);
		// Current behaviour: a template with null config is treated like a missing template (defaults)
		assertIndexDefaults(configCache.index());
	}

	void saveIndexConfig(String json) throws Exception {
		var template = templateRepository.findByTemplateAndOrigin("_config/index", "").orElseThrow();
		template.setConfig((ObjectNode) objectMapper.readTree(json));
		templateRepository.save(template);
	}

	// ---------- Script ----------

	@Test
	void scriptEmptyObjectGivesAllDefaults() throws Exception {
		assertScriptDefaults(objectMapper.readValue("{}", Script.class));
		assertScriptDefaults(objectMapper.convertValue(objectMapper.createObjectNode(), Script.class));
	}

	@Test
	void scriptPartialObjectOnlyChangesGivenFields() throws Exception {
		var script = objectMapper.readValue("{\"language\": \"python\", \"script\": \"print(1)\"}", Script.class);
		assertThat(script.getLanguage()).isEqualTo("python");
		assertThat(script.getScript()).isEqualTo("print(1)");
		assertThat(script.getTimeoutMs()).isEqualTo(30_000);
		assertThat(script.getFormat()).isEqualTo("json");
		assertThat(script.getRequirements()).isNull();
	}

	@Test
	void scriptExplicitNullIntKeepsDefault() throws Exception {
		// An explicit null keeps the 30s default (0 would time out every script immediately)
		var script = objectMapper.readValue("{\"timeoutMs\": null}", Script.class);
		assertThat(script.getTimeoutMs()).isEqualTo(30_000);
		assertThat(script.getLanguage()).isEqualTo("javascript");
	}

	@Test
	void scriptExplicitNullStringKeepsDefault() throws Exception {
		// An explicit null keeps the defaults, ScriptRunner calls config.getFormat().toLowerCase()
		var script = objectMapper.readValue("{\"language\": null, \"format\": null, \"script\": null}", Script.class);
		assertThat(script.getLanguage()).isEqualTo("javascript");
		assertThat(script.getFormat()).isEqualTo("json");
		assertThat(script.getScript()).isNull();
		assertThat(script.getTimeoutMs()).isEqualTo(30_000);
		assertThat(objectMapper.writeValueAsString(script)).isEqualTo(SCRIPT_DEFAULT_JSON);
	}

	@Test
	void scriptNumericCoercion() throws Exception {
		assertThat(objectMapper.readValue("{\"timeoutMs\": \"5000\"}", Script.class).getTimeoutMs()).isEqualTo(5000);
		assertThat(objectMapper.readValue("{\"timeoutMs\": 1500.7}", Script.class).getTimeoutMs()).isEqualTo(1500);
		assertThatThrownBy(() -> objectMapper.readValue("{\"timeoutMs\": \"abc\"}", Script.class))
			.isInstanceOf(InvalidFormatException.class);
		assertThatThrownBy(() -> objectMapper.convertValue(objectMapper.readTree("{\"timeoutMs\": \"abc\"}"), Script.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasCauseInstanceOf(InvalidFormatException.class);
	}

	@Test
	void scriptUnknownPropertyIsIgnored() throws Exception {
		assertScriptDefaults(objectMapper.readValue("{\"unknown\": true}", Script.class));
	}

	@Test
	void scriptDefaultSerialization() throws Exception {
		assertThat(objectMapper.writeValueAsString(Script.builder().build())).isEqualTo(SCRIPT_DEFAULT_JSON);
		assertThat(objectMapper.writeValueAsString(Script.builder().requirements("uuid").script("x").build()))
			.isEqualTo("{\"timeoutMs\":30000,\"language\":\"javascript\",\"format\":\"json\",\"requirements\":\"uuid\",\"script\":\"x\"}");
	}

	@Test
	void scriptPluginGetConfig() throws Exception {
		var plugin = new Plugin();
		plugin.setTag(SCRIPT_TAG);
		plugin.setConfig((ObjectNode) objectMapper.readTree("{}"));
		assertScriptDefaults(plugin.getConfig(Script.class));

		plugin.setConfig((ObjectNode) objectMapper.readTree("{\"format\": \"yaml\", \"other\": 1}"));
		var script = plugin.getConfig(Script.class);
		assertThat(script.getFormat()).isEqualTo("yaml");
		assertThat(script.getLanguage()).isEqualTo("javascript");
		assertThat(script.getTimeoutMs()).isEqualTo(30_000);
	}

	@Test
	void scriptRefGetPlugin() throws Exception {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setPlugin(SCRIPT_TAG, objectMapper.readTree("{}"));
		assertScriptDefaults(ref.getPlugin(SCRIPT_TAG, Script.class));

		ref.setPlugin(SCRIPT_TAG, objectMapper.readTree("{\"timeoutMs\": 100}"));
		var script = ref.getPlugin(SCRIPT_TAG, Script.class);
		assertThat(script.getTimeoutMs()).isEqualTo(100);
		assertThat(script.getLanguage()).isEqualTo("javascript");
		assertThat(script.getFormat()).isEqualTo("json");

		assertThat(ref.getPlugin("plugin/missing", Script.class)).isNull();
	}

	@Test
	void scriptConfigCacheGetConfigFromSavedRef() throws Exception {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>());
		ref.setPlugin(SCRIPT_TAG, objectMapper.readTree("{\"language\": \"shell\"}"));
		refRepository.save(ref);

		var script = configCache.getConfig(URL, "", SCRIPT_TAG, Script.class);
		assertThat(script.getLanguage()).isEqualTo("shell");
		assertThat(script.getTimeoutMs()).isEqualTo(30_000);
		assertThat(script.getFormat()).isEqualTo("json");
		assertThat(script.getScript()).isNull();

		// Missing Ref: defaults from convertValue({})
		assertScriptDefaults(configCache.getConfig("test:missing", "", SCRIPT_TAG, Script.class));
	}

	@Test
	void scriptConfigCacheGetPluginConfigEmpty() {
		savePlugin("{}");
		assertScriptDefaults(configCache.getPluginConfig(SCRIPT_TAG, "", Script.class).orElseThrow());
	}

	@Test
	void scriptConfigCacheGetPluginConfigNullConfig() {
		savePlugin(null);
		// Plugin without config falls back to convertValue({})
		assertScriptDefaults(configCache.getPluginConfig(SCRIPT_TAG, "", Script.class).orElseThrow());
	}

	@Test
	void scriptConfigCacheGetPluginConfigPartial() {
		savePlugin("{\"timeoutMs\": 1000, \"requirements\": \"requests\", \"unknown\": 1}");
		var script = configCache.getPluginConfig(SCRIPT_TAG, "", Script.class).orElseThrow();
		assertThat(script.getTimeoutMs()).isEqualTo(1000);
		assertThat(script.getRequirements()).isEqualTo("requests");
		assertThat(script.getLanguage()).isEqualTo("javascript");
		assertThat(script.getFormat()).isEqualTo("json");
		assertThat(script.getScript()).isNull();
	}

	@Test
	void scriptConfigCacheGetPluginConfigMissing() {
		assertThat(configCache.getPluginConfig(SCRIPT_TAG, "", Script.class)).isEmpty();
	}

	void savePlugin(String config) {
		var plugin = new Plugin();
		plugin.setTag(SCRIPT_TAG);
		plugin.setOrigin("");
		try {
			plugin.setConfig(config == null ? null : (ObjectNode) objectMapper.readTree(config));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		pluginRepository.save(plugin);
	}
}
