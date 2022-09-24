package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
public class ValidateIT {

	@Autowired
	Validate validate;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	static final String URL = "https://www.example.com/";

	@Test
	void testValidateRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);

		validate.ref(ref, false);
	}

	@Test
	void testValidateRefWithInvalidPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
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
		ref.setTags(List.of("+user/tester", "plugin/test"));

		assertThatThrownBy(() -> validate.ref(ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithPlugin() throws IOException {
		var mapper = new ObjectMapper();
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
		ref.setTags(List.of("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			}
		}"""));

		validate.ref(ref, false);
	}

	@Test
	void testValidateRefWithPluginExtraFailed() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
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
		ref.setTags(List.of("+user/tester", "plugin/test"));
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


		assertThatThrownBy(() -> validate.ref(ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithSchemalessPluginFailed() throws IOException {
		var mapper = new ObjectMapper();
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			}
		}"""));

		assertThatThrownBy(() -> validate.ref(ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithPluginDefaults() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setDefaults(mapper.readTree("""
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
		ref.setTags(List.of("+user/tester", "plugin/test"));

		validate.ref(ref, true);
	}
}
