package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jasper.IntegrationTest;
import jasper.component.dto.Bundle;
import jasper.domain.Ref;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that ScriptRunner reads the same Bundle from JSON (primary mapper) and YAML ("yamlMapper").
 */
@IntegrationTest
public class YamlBundleIT {
	static final Instant MODIFIED = Instant.parse("2024-01-02T03:04:05.123456Z");
	static final Instant PUBLISHED = Instant.parse("2024-01-02T03:04:05Z");

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	@Qualifier("yamlMapper")
	ObjectMapper yamlMapper;

	static final String JSON = """
		{
		  "ref": [{
		    "url": "https://example.com/1",
		    "title": "Title",
		    "comment": "Line 1\\nLine 2",
		    "tags": ["public", "+plugin/test"],
		    "sources": [],
		    "plugins": {"+plugin/test": {"n": 1, "f": 1.5, "b": true, "s": "x", "a": [], "z": null}},
		    "modified": "2024-01-02T03:04:05.123456Z",
		    "published": "2024-01-02T03:04:05Z"
		  }, {
		    "url": "https://example.com/2"
		  }],
		  "ext": [],
		  "plugin": [{"tag": "plugin/test", "config": {"default": true}}]
		}
		""";

	static final String YAML = """
		ref:
		- url: https://example.com/1
		  title: Title
		  comment: "Line 1\\nLine 2"
		  tags:
		  - public
		  - +plugin/test
		  sources: []
		  plugins:
		    +plugin/test:
		      n: 1
		      f: 1.5
		      b: true
		      s: x
		      a: []
		      z: null
		  modified: 2024-01-02T03:04:05.123456Z
		  published: '2024-01-02T03:04:05Z'
		- url: https://example.com/2
		ext: []
		plugin:
		- tag: plugin/test
		  config:
		    default: true
		""";

	Bundle readJson(String output) throws Exception {
		return objectMapper.readValue(output, new TypeReference<Bundle>() {});
	}

	Bundle readYaml(String output) throws Exception {
		return yamlMapper.readValue(output, new TypeReference<Bundle>() {});
	}

	@Test
	void mappersAreConfiguredAsExpected() {
		assertThat(yamlMapper).isNotSameAs(objectMapper);
		assertThat(yamlMapper.getFactory()).isInstanceOf(YAMLFactory.class);
		assertThat(objectMapper.getFactory()).isNotInstanceOf(YAMLFactory.class);
	}

	@Test
	void jsonAndYamlGiveTheSameBundle() throws Exception {
		var json = readJson(JSON);
		var yaml = readYaml(YAML);

		for (var bundle : List.of(json, yaml)) {
			assertThat(bundle.getRef()).hasSize(2);
			var ref = bundle.getRef()[0];
			assertThat(ref.getUrl()).isEqualTo("https://example.com/1");
			assertThat(ref.getOrigin()).isEqualTo("");
			assertThat(ref.getTitle()).isEqualTo("Title");
			assertThat(ref.getComment()).isEqualTo("Line 1\nLine 2");
			assertThat(ref.getTags()).containsExactly("public", "+plugin/test");
			// Empty list stays empty, missing list stays null
			assertThat(ref.getSources()).isNotNull().isEmpty();
			assertThat(ref.getAlternateUrls()).isNull();
			assertThat(ref.getModified()).isEqualTo(MODIFIED);
			assertThat(ref.getPublished()).isEqualTo(PUBLISHED);
			assertThat(ref.getMetadata()).isNull();
			var plugin = ref.getPlugins().get("+plugin/test");
			assertThat(plugin.get("n").isInt()).isTrue();
			assertThat(plugin.get("n").intValue()).isEqualTo(1);
			assertThat(plugin.get("f").doubleValue()).isEqualTo(1.5);
			assertThat(plugin.get("b").booleanValue()).isTrue();
			assertThat(plugin.get("s").textValue()).isEqualTo("x");
			assertThat(plugin.get("a").isArray()).isTrue();
			assertThat(plugin.get("a").size()).isZero();
			assertThat(plugin.has("z")).isTrue();
			assertThat(plugin.get("z").isNull()).isTrue();

			var minimal = bundle.getRef()[1];
			assertThat(minimal.getUrl()).isEqualTo("https://example.com/2");
			assertThat(minimal.getTitle()).isNull();
			assertThat(minimal.getTags()).isNull();
			assertThat(minimal.getSources()).isNull();
			assertThat(minimal.getPlugins()).isNull();

			assertThat(bundle.getExt()).isNotNull().isEmpty();
			assertThat(bundle.getPlugin()).hasSize(1);
			assertThat(bundle.getPlugin()[0].getTag()).isEqualTo("plugin/test");
			assertThat(bundle.getPlugin()[0].getOrigin()).isEqualTo("");
			assertThat(bundle.getPlugin()[0].getConfig().get("default").booleanValue()).isTrue();
			assertThat(bundle.getTemplate()).isNull();
			assertThat(bundle.getUser()).isNull();
		}

		assertThat((Object) yaml.getRef()[0].getPlugins()).isEqualTo(json.getRef()[0].getPlugins());
		assertThat((Object) yaml.getPlugin()[0].getConfig()).isEqualTo(json.getPlugin()[0].getConfig());
	}

	@Test
	void missingInstantsDefaultToNow() throws Exception {
		var before = Instant.now();
		var json = readJson("{\"ref\": [{\"url\": \"https://example.com/3\"}]}").getRef()[0];
		var yaml = readYaml("ref:\n- url: https://example.com/3\n").getRef()[0];
		var after = Instant.now();
		for (var ref : List.of(json, yaml)) {
			assertThat(ref.getModified()).isBetween(before, after);
			assertThat(ref.getPublished()).isBetween(before, after);
			assertThat(ref.getCreated()).isBetween(before, after);
		}
	}

	@Test
	void numericInstants() throws Exception {
		// Numbers are read as epoch seconds (with fraction) by both mappers
		var json = readJson("{\"ref\": [{\"url\": \"u\", \"published\": 1704164645, \"modified\": 1704164645.123456}]}").getRef()[0];
		var yaml = readYaml("ref:\n- url: u\n  published: 1704164645\n  modified: 1704164645.123456\n").getRef()[0];
		for (var ref : List.of(json, yaml)) {
			assertThat(ref.getPublished()).isEqualTo(PUBLISHED);
			assertThat(ref.getModified()).isEqualTo(MODIFIED);
		}
	}

	@Test
	void unknownPropertiesAreIgnored() throws Exception {
		var json = readJson("{\"ref\": [{\"url\": \"u\", \"unknown\": 1}], \"other\": []}");
		var yaml = readYaml("ref:\n- url: u\n  unknown: 1\nother: []\n");
		assertThat(json.getRef()[0].getUrl()).isEqualTo("u");
		assertThat(yaml.getRef()[0].getUrl()).isEqualTo("u");
	}

	@Test
	void emptyBundle() throws Exception {
		var json = readJson("{}");
		var yaml = readYaml("{}");
		for (var bundle : List.of(json, yaml)) {
			assertThat(bundle.getRef()).isNull();
			assertThat(bundle.getExt()).isNull();
			assertThat(bundle.getPlugin()).isNull();
			assertThat(bundle.getTemplate()).isNull();
			assertThat(bundle.getUser()).isNull();
		}
	}

	@Test
	void yamlRoundTripOfScriptInput() throws Exception {
		// ScriptRunner writes the script input with the same mapper used to read the output
		var ref = new Ref();
		ref.setUrl("https://example.com/1");
		ref.setTags(new ArrayList<>(List.of("public")));
		ref.setSources(new ArrayList<>());
		ref.setModified(MODIFIED);
		ref.setPublished(PUBLISHED);
		ref.setCreated(PUBLISHED);

		var yamlText = yamlMapper.writeValueAsString(ref);
		var jsonText = objectMapper.writeValueAsString(ref);
		// Instants are written as ISO-8601 strings, not timestamps
		assertThat(yamlText).contains("2024-01-02T03:04:05.123456Z");
		assertThat(jsonText).contains("\"modified\":\"2024-01-02T03:04:05.123456Z\"");
		assertThat(jsonText).contains("\"published\":\"2024-01-02T03:04:05Z\"");
		// Nulls are written
		assertThat(jsonText).contains("\"title\":null");

		var fromYaml = yamlMapper.readValue(yamlText, Ref.class);
		var fromJson = objectMapper.readValue(jsonText, Ref.class);
		for (var r : List.of(fromYaml, fromJson)) {
			assertThat(r.getUrl()).isEqualTo("https://example.com/1");
			assertThat(r.getTags()).containsExactly("public");
			assertThat(r.getSources()).isNotNull().isEmpty();
			assertThat(r.getAlternateUrls()).isNull();
			assertThat(r.getTitle()).isNull();
			assertThat(r.getModified()).isEqualTo(MODIFIED);
			assertThat(r.getPublished()).isEqualTo(PUBLISHED);
			assertThat(r.getCreated()).isEqualTo(PUBLISHED);
		}
	}
}
