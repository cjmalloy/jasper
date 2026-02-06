package jasper.component;

import jasper.domain.Plugin;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ValidateRefTest {

	@InjectMocks
	Validate validate;

	@Mock
	ConfigCache configs;

	JsonMapper mapper = new JsonMapper();

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		validate.jsonMapper = mapper;
	}

	@Test
	void testPluginDefaults() {
		var ref = new Ref();
		ref.setTags(List.of("plugin/test", "plugin/other"));

		var pluginTest = new Plugin();
		pluginTest.setTag("plugin/test");
		pluginTest.setDefaults((ObjectNode) mapper.readTree("""
        {
            "name": "test",
            "list": ["a"]
        }"""));

		var pluginOther = new Plugin();
		pluginOther.setTag("plugin/other");
		pluginOther.setDefaults((ObjectNode) mapper.readTree("""
        {
            "name": "other",
            "list": ["b"]
        }"""));

		when(configs.getPlugin("plugin/test", "")).thenReturn(Optional.of(pluginTest));
		when(configs.getPlugin("plugin/other", "")).thenReturn(Optional.of(pluginOther));

		var defaults = validate.pluginDefaults("", ref);

		assertThat(defaults.has("plugin/test")).isTrue();
		assertThat(defaults.get("plugin/test").get("name").asText()).isEqualTo("test");
		assertThat(defaults.has("plugin/other")).isTrue();
		assertThat(defaults.get("plugin/other").get("name").asText()).isEqualTo("other");
	}

	@Test
	void testPluginDefaultsWithRefPlugins() throws Exception {
		var ref = new Ref();
		ref.setTags(List.of("plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
        {
            "plugin/test": {
                "name": "overridden",
                "list": ["c"]
            }
        }"""));

		var pluginTest = new Plugin();
		pluginTest.setTag("plugin/test");
		pluginTest.setDefaults((ObjectNode) mapper.readTree("""
        {
            "name": "test",
            "list": ["a", "b"]
        }"""));

		when(configs.getPlugin("plugin/test", "")).thenReturn(Optional.of(pluginTest));

		var defaults = validate.pluginDefaults("", ref);

		assertThat(defaults.has("plugin/test")).isTrue();
		assertThat(defaults.get("plugin/test").get("name").asText()).isEqualTo("overridden");

		// Arrays should be OVERRIDDEN according to recent merge logic changes
		assertThat(defaults.get("plugin/test").get("list")).hasSize(1);
		assertThat(defaults.get("plugin/test").get("list").get(0).asText()).isEqualTo("c");
	}

	@Test
	void testPluginDefaultsExpandTags() throws Exception {
		var ref = new Ref();
		// expandTags will include "plugin/test/sub" and "plugin/test"
		ref.setTags(List.of("plugin/test/sub"));

		var pluginSub = new Plugin();
		pluginSub.setTag("plugin/test/sub");
		pluginSub.setDefaults((ObjectNode) mapper.readTree("{\"sub\": true}"));

		var pluginParent = new Plugin();
		pluginParent.setTag("plugin/test");
		pluginParent.setDefaults((ObjectNode) mapper.readTree("{\"parent\": true}"));

		when(configs.getPlugin("plugin/test/sub", "")).thenReturn(Optional.of(pluginSub));
		when(configs.getPlugin("plugin/test", "")).thenReturn(Optional.of(pluginParent));

		var defaults = validate.pluginDefaults("", ref);

		assertThat(defaults.has("plugin/test/sub")).isTrue();
		assertThat(defaults.get("plugin/test/sub").get("sub").asBoolean()).isTrue();
		assertThat(defaults.has("plugin/test")).isTrue();
		assertThat(defaults.get("plugin/test").get("parent").asBoolean()).isTrue();
	}

	@Test
	void testPluginDefaultsOverlap() throws Exception {
		var ref = new Ref();
		ref.setTags(List.of("plugin/test/a", "plugin/test/b"));

		var pluginA = new Plugin();
		pluginA.setTag("plugin/test/a");
		pluginA.setDefaults((ObjectNode) mapper.readTree("{\"a\": true}"));

		var pluginB = new Plugin();
		pluginB.setTag("plugin/test/b");
		pluginB.setDefaults((ObjectNode) mapper.readTree("{\"b\": true}"));

		var pluginParent = new Plugin();
		pluginParent.setTag("plugin/test");
		pluginParent.setDefaults((ObjectNode) mapper.readTree("{\"parent\": true, \"overridden\": false}"));

		// User overrides parent plugin defaults
		ref.setPlugins((ObjectNode) mapper.readTree("""
        {
            "plugin/test": {
                "overridden": true
            }
        }"""));

		when(configs.getPlugin("plugin/test/a", "")).thenReturn(Optional.of(pluginA));
		when(configs.getPlugin("plugin/test/b", "")).thenReturn(Optional.of(pluginB));
		when(configs.getPlugin("plugin/test", "")).thenReturn(Optional.of(pluginParent));

		var defaults = validate.pluginDefaults("", ref);

		assertThat(defaults.has("plugin/test/a")).isTrue();
		assertThat(defaults.has("plugin/test/b")).isTrue();
		assertThat(defaults.has("plugin/test")).isTrue();
		assertThat(defaults.get("plugin/test").get("overridden").asBoolean()).isTrue();
		assertThat(defaults.get("plugin/test").get("parent").asBoolean()).isTrue();
	}
}
