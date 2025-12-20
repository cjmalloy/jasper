package jasper.component;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.errors.InvalidPluginException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
public class ValidateRefIT {

	@Autowired
	Validate validate;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	JsonMapper mapper;

	static final String URL = "https://www.example.com/";

	@Test
	void testValidateRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester"));
		refRepository.save(ref);

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithInvalidPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithInvalidPluginDefaults() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		plugin.setDefaults((ObjectNode) mapper.readTree("""
		{
			"invalid": "defaults"
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "plugin/test")));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			}
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithOptionalPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"optionalProperties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "plugin/test")));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithStringPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "type": "string" },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": "test"
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithBooleanPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "type": "boolean" },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": true
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithNumberPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "type": "uint32" },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": 100
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithArrayPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "elements": { "type": "string" } },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": ["test", "works"]
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithPluginExtraFailed() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			},
			"extraStuff": {
				"is": "not allowed"
			}
		}"""));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithSchemalessPluginFailed() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			}
		}"""));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithPluginDefaults() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setDefaults((ObjectNode) mapper.readTree("""
		{
			"name": "Alice",
			"age": 100
		}"""));
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));

		validate.ref("", ref, false);
	}
}
